import type { Metadata } from "next";
import { PageHeader } from "@/components/layout/page-header";
import { PreferencesForm } from "@/components/settings/preferences-form";

export const metadata: Metadata = { title: "Settings" };

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" description="Configure how TaskFlow interprets dates and your daily capacity." />
      <div className="max-w-2xl">
        <PreferencesForm />
      </div>
    </>
  );
}
