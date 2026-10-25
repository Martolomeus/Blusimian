import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { Button } from "@/components/ui";
import { CreateRecordModal } from "@/components/CreateRecordModal";
import { text } from "@/lib/entity/entityConfig";
import { useRecordList } from "@/lib/entity/records";

// The columns are hand-picked for now. (A RecordList driven by the entity schema can
// replace this simple table later.)
export default function CompaniesPage() {
  const navigate = useNavigate();
  const [showCreate, setShowCreate] = useState(false);
  const { data, isLoading, error } = useRecordList("COMPANY");

  return (
    <main className="page-content">
      <div className="page-header">
        <h1 className="page-title">Companies</h1>
        <Button onClick={() => setShowCreate(true)}>New Company</Button>
      </div>

      {isLoading && <p className="page-empty">Loading…</p>}
      {error && <p className="page-error">{error.message}</p>}
      {data && data.length === 0 && <p className="page-empty">No companies found.</p>}

      {data && data.length > 0 && (
        <table className="list-table">
          <thead>
            <tr><th>Name</th><th>Type</th><th>Industry</th></tr>
          </thead>
          <tbody>
            {data.map((c) => (
              <tr key={c.id} onClick={() => navigate(`/companies/${c.id}`)}>
                <td>
                  <Link className="record-name-link" to={`/companies/${c.id}`} onClick={(e) => e.stopPropagation()}>
                    {text(c.name)}
                  </Link>
                </td>
                <td className="cell-muted">{text(c.type) || "—"}</td>
                <td className="cell-muted">{text(c.industry) || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <CreateRecordModal
        entityType="COMPANY"
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSuccess={(id) => navigate(`/companies/${id}`)}
      />
    </main>
  );
}
