import { describe, expect, it } from "vitest";
import userEvent from "@testing-library/user-event";
import { screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/mocks/server";
import { renderWithProviders } from "@/test/renderWithProviders";
import { axe } from "@/test/axe";
import { NotificationBell } from "@/features/notifications/components/NotificationBell";

const API = "http://localhost:8080";

const UNREAD = [
  {
    id: 1,
    type: "PAYMENT_RECEIPT_APPROVED",
    read: false,
    createdAt: "2026-10-01T10:00:00",
  },
  {
    id: 2,
    type: "PAYMENT_RECEIPT_APPROVED",
    read: false,
    createdAt: "2026-10-01T11:00:00",
  },
];

describe("NotificationBell accessibility", () => {
  it("notificationBellHasNoAxeViolations", async () => {
    server.use(
      http.get(`${API}/me/notifications`, () => HttpResponse.json(UNREAD)),
    );
    const { container } = renderWithProviders(<NotificationBell />);
    await screen.findByRole("button", { name: /sin leer/ }, { timeout: 5000 });

    expect(await axe(container)).toHaveNoViolations();
  });

  it("notificationBellAnnouncesUnreadCountInLabel", async () => {
    server.use(
      http.get(`${API}/me/notifications`, () => HttpResponse.json(UNREAD)),
    );
    renderWithProviders(<NotificationBell />);

    expect(
      await screen.findByRole("button", { name: "Notificaciones, 2 sin leer" }),
    ).toBeInTheDocument();
  });

  it("notificationBellOpenListHasNoAxeViolations", async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/me/notifications`, () =>
        HttpResponse.json([
          ...UNREAD,
          { id: 3, type: "PAYMENT_CHARGED_BACK", read: true, message: "Revertido", createdAt: "2026-10-01T12:00:00" },
        ]),
      ),
    );
    renderWithProviders(<NotificationBell />);
    await user.click(await screen.findByRole("button", { name: "Notificaciones, 2 sin leer" }));
    await screen.findByText("Pago revertido por contracargo");

    expect(await axe(document.body)).toHaveNoViolations();
  });
});
