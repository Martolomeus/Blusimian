import { SettingsCard, type SettingsCardProps } from "@/components/settings/SettingsCard";
import { LayoutIcon } from "@/lib/icons";

// In overview order. A section is listed once its page exists; adding one is an entry
// here plus its routes in router.tsx.
const SECTIONS: SettingsCardProps[] = [
  {
    title: "Entity layouts",
    description: "Arrange the sections and fields shown on contact, company and deal pages.",
    to: "/settings/layouts",
    icon: LayoutIcon,
  },
];

export default function SettingsPage() {
  return (
    <main className="page-content">
      <div className="page-header">
        <div>
          <h1 className="page-title">Settings</h1>
          <p className="page-intro">Configure how Blusimian works for your organisation.</p>
        </div>
      </div>

      <div className="settings-grid">
        {SECTIONS.map((section) => (
          <SettingsCard key={section.to} {...section} />
        ))}
      </div>
    </main>
  );
}
