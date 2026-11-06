"use client";

import { useState, type KeyboardEvent } from "react";
import { SendHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

const MAX_LENGTH = 2000;

interface ChatComposerProps {
  onSend: (text: string) => void;
  disabled?: boolean;
}

export function ChatComposer({ onSend, disabled }: ChatComposerProps) {
  const [value, setValue] = useState("");
  const canSend = value.trim().length > 0 && !disabled;

  function submit() {
    if (!canSend) return;
    onSend(value);
    setValue("");
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <form
      className="flex items-end gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <label htmlFor="assistant-input" className="sr-only">
        Message the assistant
      </label>
      <Textarea
        id="assistant-input"
        value={value}
        onChange={(event) => setValue(event.target.value.slice(0, MAX_LENGTH))}
        onKeyDown={onKeyDown}
        placeholder="Ask TaskAgent… e.g. “Create a high-priority task for tomorrow”"
        rows={2}
        maxLength={MAX_LENGTH}
        disabled={disabled}
        className="min-h-[2.75rem] max-h-40 resize-none"
      />
      <Button type="submit" size="icon-lg" aria-label="Send message" disabled={!canSend} className="bg-brand-gradient text-white hover:opacity-90">
        <SendHorizontal />
      </Button>
    </form>
  );
}
