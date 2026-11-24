package com.taskflow.ai.conversation;

/**
 * The conversation does not exist or belongs to another user. Both cases look the same to the caller (HTTP 404),
 * so a conversation id cannot be used to probe other users' data.
 */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversation not found. Start a new conversation.");
    }
}
