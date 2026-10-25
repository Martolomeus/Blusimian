import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router";
import { createPortal } from "react-dom";
import { ENTITIES } from "@/lib/entity/entityConfig";
import { SettingsIcon } from "@/lib/icons";

const navItems = Object.values(ENTITIES).map((entity) => ({
  href: entity.path,
  label: entity.plural,
  Icon: entity.icon,
}));

export default function Sidebar() {
  const { pathname } = useLocation();
  const [hovered, setHovered] = useState(false);
  const [mounted, setMounted] = useState(false);

  useEffect(() => setMounted(true), []);

  return (
    <>
      {mounted &&
        createPortal(
          <div className={`sidebar-overlay${hovered ? " sidebar-overlay--visible" : ""}`} />,
          document.body
        )}
      <aside
        className="sidebar"
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <div className="sidebar-panel">
          <div className="sidebar-header">
            <img
              src="/blusimian-mark-white.svg"
              alt="Blusimian"
              width={38}
              height={37}
              className="sidebar-logo"
            />
            <span className="nav-label sidebar-logotype">
              <span className="sidebar-logotype-name">Blusimian</span>
              <span className="sidebar-logotype-sub">S o f t w a r e</span>
            </span>
          </div>
          <nav className="sidebar-nav">
            {navItems.map(({ href, label, Icon }) => (
              <Link
                key={href}
                to={href}
                className={`sidebar-nav-link${pathname.startsWith(href) ? " sidebar-nav-link--active" : ""}`}
              >
                <Icon className="nav-icon" size={20} />
                <span className="nav-label">{label}</span>
              </Link>
            ))}
          </nav>
          {/* .sidebar-nav grows, so this sits at the bottom with no spacer. */}
          <div className="sidebar-footer">
            <Link
              to="/settings"
              className={`sidebar-nav-link${pathname.startsWith("/settings") ? " sidebar-nav-link--active" : ""}`}
            >
              <SettingsIcon className="nav-icon" size={20} />
              <span className="nav-label">Settings</span>
            </Link>
          </div>
        </div>
      </aside>
    </>
  );
}
