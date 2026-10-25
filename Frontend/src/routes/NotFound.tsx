import { Link } from "react-router";

export default function NotFound() {
  return (
    <div className="page-content">
      <h1 className="page-title">Not found</h1>
      <p className="page-empty">That page doesn’t exist.</p>
      <Link to="/contacts" className="record-name-link">Go to Contacts</Link>
    </div>
  );
}
