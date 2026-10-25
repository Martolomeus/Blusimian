import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { Button } from "@/components/ui";
import { CreateRecordModal } from "@/components/CreateRecordModal";
import { text } from "@/lib/entity/entityConfig";
import { useRecordList } from "@/lib/entity/records";

// The columns are hand-picked for now. (A RecordList driven by the entity schema can
// replace this simple table later.)
export default function ContactsPage() {
  const navigate = useNavigate();
  const [showCreate, setShowCreate] = useState(false);
  const { data, isLoading, error } = useRecordList("CONTACT");

  return (
    <main className="page-content">
      <div className="page-header">
        <h1 className="page-title">Contacts</h1>
        <Button onClick={() => setShowCreate(true)}>New Contact</Button>
      </div>

      {isLoading && <p className="page-empty">Loading…</p>}
      {error && <p className="page-error">{error.message}</p>}
      {data && data.length === 0 && <p className="page-empty">No contacts found.</p>}

      {data && data.length > 0 && (
        <table className="list-table">
          <thead>
            <tr><th>Name</th><th>Email</th><th>Phone</th></tr>
          </thead>
          <tbody>
            {data.map((c) => (
              <tr key={c.id} onClick={() => navigate(`/contacts/${c.id}`)}>
                <td>
                  <Link className="record-name-link" to={`/contacts/${c.id}`} onClick={(e) => e.stopPropagation()}>
                    {text(c.firstName)} {text(c.lastName)}
                  </Link>
                </td>
                <td className="cell-muted">{text(c.email) || "—"}</td>
                <td className="cell-muted">{text(c.phone) || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <CreateRecordModal
        entityType="CONTACT"
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSuccess={(id) => navigate(`/contacts/${id}`)}
      />
    </main>
  );
}
