import type { Metadata } from "next";
import { AssistantChat } from "@/components/assistant/assistant-chat";
import { PageHeader } from "@/components/layout/page-header";

export const metadata: Metadata = { title: "AI Assistant" };

export default function AssistantPage() {
  return (
    <>
      <PageHeader title="AI Assistant" description="Manage your tasks in plain language. Switch between the single TaskAgent and the TaskFlow Supervisor that routes to specialised agents (LangChain4j + OpenAI)." />
      <AssistantChat />
    </>
  );
}
