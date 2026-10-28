import { SettingsCard, type SettingsCardProps } from "@/components/settings/SettingsCard";
import { SettingsSection } from "@/components/settings/SettingsSection";
import { ENTITIES } from "@/lib/entity/entityConfig";
import type { EntityType } from "@/lib/entity/types";
import { entitySettingsPath } from "./paths";

type OverviewSection = { key: string; title: string; description?: string; cards: SettingsCardProps[] };

function entitySettingsCard(type: EntityType): SettingsCardProps {
  const entity = ENTITIES[type];
  return {
    title: `${entity.singular} settings`,
    description: `Layouts for ${entity.singular.toLowerCase()} records.`,
    to: entitySettingsPath(type),
    icon: entity.icon,
  };
}

// In overview order; an entity's card in the section it belongs to, in sidebar order
// within it. A section is listed once it has a card.
const SECTIONS: OverviewSection[] = [
  { key: "crm", title: "CRM", cards: [entitySettingsCard("CONTACT"), entitySettingsCard("COMPANY")] },
  { key: "sales", title: "Sales", cards: [entitySettingsCard("DEAL")] },
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

      {SECTIONS.map(({ key, title, description, cards }) => (
        <SettingsSection key={key} storageKey={`overview/${key}`} title={title} description={description}>
          <div className="settings-grid">
            {cards.map((card) => (
              <SettingsCard key={card.to} {...card} />
            ))}
          </div>
        </SettingsSection>
      ))}
    </main>
  );
}
