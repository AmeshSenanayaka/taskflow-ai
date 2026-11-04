import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

/** Logo lock-up: gradient check-mark tile plus the wordmark, matching the series artwork. */
export function Brand({ onDark = false }: { onDark?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-brand-gradient text-white shadow-sm shadow-violet-500/30">
        <Check className="size-5" strokeWidth={3} aria-hidden="true" />
      </span>
      <span className={cn("text-base font-bold tracking-tight", onDark ? "text-white" : "text-brand-gradient")}>TaskFlow AI</span>
    </div>
  );
}
