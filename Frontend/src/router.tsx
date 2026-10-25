import { createBrowserRouter, Navigate, type RouteObject } from "react-router";
import type { ComponentType } from "react";
import AppLayout from "./routes/AppLayout";
import NotFound from "./routes/NotFound";
import EntityDetailPage from "./components/entity/EntityDetailPage";

// Lazy-load each page's default export as a route Component, so every page is
// code-split and fetched on navigation. Mirrors the Dolfin router structure.
// Detail pages are all the one generic EntityDetailPage, told which entity to draw.
function lazyPage(
  importer: () => Promise<{ default: ComponentType }>,
): Pick<RouteObject, "lazy"> {
  return { lazy: async () => ({ Component: (await importer()).default }) };
}

export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/contacts" replace /> },

      { path: "contacts", ...lazyPage(() => import("./features/contacts/ContactsPage")) },
      { path: "contacts/:id", element: <EntityDetailPage entityType="CONTACT" /> },

      { path: "companies", ...lazyPage(() => import("./features/companies/CompaniesPage")) },
      { path: "companies/:id", element: <EntityDetailPage entityType="COMPANY" /> },

      { path: "deals", ...lazyPage(() => import("./features/deals/DealsPage")) },
      { path: "deals/:id", element: <EntityDetailPage entityType="DEAL" /> },

      { path: "settings", ...lazyPage(() => import("./features/settings/SettingsPage")) },
      { path: "settings/layouts", ...lazyPage(() => import("./features/settings/layouts/LayoutSettingsPage")) },
      { path: "settings/layouts/new", ...lazyPage(() => import("./features/settings/layouts/editor/NewLayoutPage")) },
      { path: "settings/layouts/:id", ...lazyPage(() => import("./features/settings/layouts/editor/LayoutEditorPage")) },

      { path: "*", element: <NotFound /> },
    ],
  },
];

export const router = createBrowserRouter(routes);
