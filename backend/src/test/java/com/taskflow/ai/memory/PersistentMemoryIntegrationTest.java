package com.taskflow.ai.memory;

import com.jayway.jsonpath.JsonPath;
import com.taskflow.ai.AbstractAiIntegrationTest;
import com.taskflow.ai.ScriptedChatModel;
import com.taskflow.ai.conversation.ConversationService;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.UserAccountService;
import com.taskflow.support.TestUsers;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.taskflow.support.TestUsers.alice;
import static com.taskflow.support.TestUsers.bob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3.4 over HTTP with PostgreSQL and the scripted model: the transcript and the conversation API, isolation
 * between users, staged commits, and retention.
 */
class PersistentMemoryIntegrationTest extends AbstractAiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private UserAccountService userAccounts;
    @Autowired
    private ConversationService conversations;
    @Autowired
    private ChatMemoryRepository memoryRepository;
    @Autowired
    @Qualifier("supervisorMemoryStore")
    private PostgresChatMemoryStore supervisorStore;
    @Autowired
    private Clock clock;

    private UUID aliceId;

    @BeforeEach
    void reset() {
        model.reset();
        taskRepository.deleteAll();
        aliceId = userAccounts.resolve(TestUsers.ALICE_SUBJECT, "alice").id();
        userAccounts.resolve(TestUsers.BOB_SUBJECT, "bob");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aTurnIsStoredAsTranscriptAndAsMemoryAndListedForItsOwner() throws Exception {
        String conversation = newConversation(alice(), "supervisor");
        model.invokeAgent("SummaryAgent", "How am I doing?")
                .toolCall("getTaskStatistics", "{}")
                .text("You have no tasks yet.")
                .done("Summarised.");

        chat(conversation, "How am I doing?");

        mockMvc.perform(get("/api/ai/conversations/{id}/messages", conversation).with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("How am I doing?"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].content").value("You have no tasks yet."));
        mockMvc.perform(get("/api/ai/conversations").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value(conversation))
                .andExpect(jsonPath("$[0].title").value("How am I doing?"))
                .andExpect(jsonPath("$[0].mode").value("supervisor"));
        assertThat(memoryRepository.loadAll(new MemoryKey(aliceId, UUID.fromString(conversation))))
                .isNotEmpty()
                .anySatisfy(json -> assertThat(json).contains("How am I doing?"));
    }

    @Test
    void anotherUserCanNeitherSeeNorReadNorDeleteAConversation() throws Exception {
        String conversation = newConversation(alice(), "supervisor");

        mockMvc.perform(get("/api/ai/conversations").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].conversationId", not(hasItem(conversation))));
        mockMvc.perform(get("/api/ai/conversations/{id}/messages", conversation).with(bob()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/ai/conversations/{id}", conversation).with(bob()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/ai/conversations/{id}/messages", conversation).with(alice()))
                .andExpect(status().isOk());
    }

    @Test
    void theMemoryStoreFailsClosedForAKeyOfAnotherUser() {
        UUID conversation = conversations.create(aliceId).id();
        signInAs(TestUsers.BOB_SUBJECT, "bob");

        assertThatThrownBy(() -> supervisorStore.getMessages(aliceId + ":" + conversation))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> supervisorStore.updateMessages(aliceId + ":" + conversation,
                List.of(UserMessage.from("injected"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(memoryRepository.loadAll(new MemoryKey(aliceId, conversation))).isEmpty();
    }

    @Test
    void aDiscardedTurnLeavesNoMemoryAndACommittedOneDoes() {
        UUID conversation = conversations.create(aliceId).id();
        signInAs(TestUsers.ALICE_SUBJECT, "alice");
        String key = aliceId + ":" + conversation;
        ConversationMemory memory = new ConversationMemory(List.of(supervisorStore));

        memory.begin(aliceId);
        supervisorStore.updateMessages(key, List.of(UserMessage.from("rejected turn")));
        // Inside the turn the agents read their own staged writes back.
        assertThat(supervisorStore.getMessages(key)).hasSize(1);
        memory.discard();
        assertThat(supervisorStore.getMessages(key)).isEmpty();

        memory.begin(aliceId);
        supervisorStore.updateMessages(key, List.of(UserMessage.from("good turn")));
        memory.commit();
        assertThat(supervisorStore.getMessages(key)).singleElement()
                .satisfies(message -> assertThat(((UserMessage) message).singleText()).isEqualTo("good turn"));
    }

    @Test
    void aStagedTurnRefusesMemoryOfAnyOtherOwner() {
        UUID conversation = conversations.create(aliceId).id();
        UUID bobId = userAccounts.resolve(TestUsers.BOB_SUBJECT, "bob").id();
        ConversationMemory memory = new ConversationMemory(List.of(supervisorStore));

        // The turn was started for bob; alice's memory key must not be readable inside it.
        memory.begin(bobId);
        try {
            assertThatThrownBy(() -> supervisorStore.getMessages(aliceId + ":" + conversation))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            memory.discard();
        }
    }

    @Test
    void deletingAConversationRemovesItsMemoryAndTranscript() throws Exception {
        String conversation = newConversation(alice(), "supervisor");
        model.invokeAgent("SummaryAgent", "How am I doing?")
                .toolCall("getTaskStatistics", "{}")
                .text("You have no tasks yet.")
                .done("Summarised.");
        chat(conversation, "How am I doing?");

        mockMvc.perform(delete("/api/ai/conversations/{id}", conversation).with(alice()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ai/conversations/{id}/messages", conversation).with(alice()))
                .andExpect(status().isNotFound());
        assertThat(memoryRepository.loadAll(new MemoryKey(aliceId, UUID.fromString(conversation)))).isEmpty();
    }

    @Test
    void retentionDeletesOnlyConversationsInactiveForLongerThanThePeriod() {
        UUID conversation = conversations.create(aliceId).id();
        Instant now = Instant.now(clock);

        conversations.deleteInactive(now.plus(Duration.ofDays(29)));
        assertThat(conversations.list(aliceId)).extracting(summary -> summary.conversationId())
                .contains(conversation.toString());

        conversations.deleteInactive(now.plus(Duration.ofDays(31)));
        assertThat(conversations.list(aliceId)).extracting(summary -> summary.conversationId())
                .doesNotContain(conversation.toString());
    }

    @Test
    void anAgentModeConversationIsRecordedAsSuch() throws Exception {
        String conversation = newConversation(alice(), "agent");

        mockMvc.perform(get("/api/ai/conversations").with(alice()))
                .andExpect(jsonPath("$[?(@.conversationId == '%s')].mode".formatted(conversation)).value("agent"));
    }

    private void chat(String conversation, String message) throws Exception {
        mockMvc.perform(post("/api/ai/supervisor/chat").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"%s\",\"message\":\"%s\"}".formatted(conversation, message)))
                .andExpect(status().isOk());
    }

    private String newConversation(RequestPostProcessor user, String mode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/conversations").param("mode", mode).with(user))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.conversationId");
    }

    private static void signInAs(String subject, String username) {
        Instant issued = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-" + username).header("alg", "none").subject(subject)
                .claim("preferred_username", username).issuedAt(issued).expiresAt(issued.plusSeconds(3600)).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
