import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { Button } from "@/components/ui";
import { CreateRecordModal } from "@/components/CreateRecordModal";
import { text } from "@/lib/entity/entityConfig";
import { useRecordList } from "@/lib/entity/records";

// The columns are hand-picked for now. (A RecordList driven by the entity schema can
// replace this simple table later.)
export default function DealsPage() {
  const navigate = useNavigate();
  const [showCreate, setShowCreate] = useState(false);
  const { data, isLoading, error } = useRecordList("DEAL");

  return (
    <main className="page-content">
      <div className="page-header">
        <h1 className="page-title">Deals</h1>
        <Button onClick={() => setShowCreate(true)}>New Deal</Button>
      </div>

      {isLoading && <p className="page-empty">Loading…</p>}
      {error && <p className="page-error">{error.message}</p>}
      {data && data.length === 0 && <p className="page-empty">No deals found.</p>}

      {data && data.length > 0 && (
        <table className="list-table">
          <thead>
            <tr><th>Deal Name</th><th>Amount</th></tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.id} onClick={() => navigate(`/deals/${d.id}`)}>
                <td>
                  <Link className="record-name-link" to={`/deals/${d.id}`} onClick={(e) => e.stopPropagation()}>
                    {text(d.dealName)}
                  </Link>
                </td>
                <td className="cell-muted">
                  {typeof d.amount === "number" ? d.amount.toLocaleString() : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <CreateRecordModal
        entityType="DEAL"
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSuccess={(id) => navigate(`/deals/${id}`)}
      />
    </main>
  );
}
