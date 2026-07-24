import { FormEvent, useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Spinner } from "../components/Spinner";
import { useAuth } from "../lib/auth";
import { useNotify } from "../lib/notify";
import { useToast } from "../lib/toast";
import { formatDateTime } from "../lib/format";

export function ProfilePage() {
  const { session, updateProfile } = useAuth();
  const { pushToast } = useToast();
  const { notifyError } = useNotify();
  const customer = session!.customer;

  const [displayName, setDisplayName] = useState(customer.displayName);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  useEffect(() => {
    setDisplayName(customer.displayName);
  }, [customer.displayName]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const name = displayName.trim();
      if (!name) {
        throw new Error("Display name is required");
      }
      if (newPassword || confirmPassword || currentPassword) {
        if (!currentPassword) {
          throw new Error("Enter your current password to change it");
        }
        if (newPassword.length < 8) {
          throw new Error("New password must be at least 8 characters");
        }
        if (newPassword !== confirmPassword) {
          throw new Error("New passwords do not match");
        }
      }
      await updateProfile({
        displayName: name,
        currentPassword: currentPassword || undefined,
        newPassword: newPassword || undefined,
      });
    },
    onSuccess: () => {
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      pushToast("Profile updated");
    },
    onError: (err: Error) => notifyError(err, "profile"),
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    saveMutation.mutate();
  }

  return (
    <div className="account-panel">
      <h2>Profile</h2>
      <p className="lede" style={{ marginTop: 0 }}>
        Update how you appear at checkout and change your password.
      </p>

      <dl className="detail-list profile-meta">
        <dt>Member since</dt>
        <dd>{formatDateTime(customer.createdAt)}</dd>
        <dt>Email</dt>
        <dd>{customer.email}</dd>
      </dl>

      <form className="auth-form profile-form" onSubmit={onSubmit}>
        <label>
          Display name
          <input
            required
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={128}
          />
        </label>

        <fieldset className="profile-password">
          <legend>Change password</legend>
          <p className="checkout-hint">Leave blank to keep your current password.</p>
          <label>
            Current password
            <input
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
          </label>
          <label>
            New password
            <input
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </label>
          <label>
            Confirm new password
            <input
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </label>
        </fieldset>

        <button className="btn btn-primary" type="submit" disabled={saveMutation.isPending}>
          {saveMutation.isPending ? <Spinner size="sm" label="Saving" /> : null}
          {saveMutation.isPending ? "Saving…" : "Save profile"}
        </button>
      </form>
    </div>
  );
}
