import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { Button, Input } from "@/components/ui";
import { useLogin } from "./auth";
import styles from "./LoginPage.module.css";

// /login: outside the app layout, so it's reachable without a session.
export default function LoginPage() {
  const navigate = useNavigate();
  const login = useLogin();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    login.mutate({ username, password }, { onSuccess: () => navigate("/", { replace: true }) });
  }

  return (
    <div className={styles.page}>
      <img src="/blusimian-mark-white.svg" alt="Blusimian" width={48} height={47} />
      <form className={styles.card} onSubmit={submit}>
        <h1 className={styles.title}>Log in</h1>
        <label className={styles.label}>
          Username
          <Input
            autoFocus
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
        </label>
        <label className={styles.label}>
          Password
          <Input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>
        {login.error && <p className={styles.error}>{login.error.message}</p>}
        <Button type="submit" disabled={login.isPending}>
          {login.isPending ? "Logging in…" : "Log in"}
        </Button>
      </form>
    </div>
  );
}
