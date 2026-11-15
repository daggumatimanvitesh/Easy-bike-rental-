import { createContext, useContext, useEffect, useState } from 'react';
import { login as loginApi } from '../services/api';

const Context = createContext(null);

function writeSession(token, user) {
  const serializedUser = JSON.stringify(user);
  // Keep the active tab's credentials independent from delayed requests in other tabs.
  sessionStorage.setItem('ebr_token', token);
  sessionStorage.setItem('ebr_user', serializedUser);
  // Preserve compatibility with older tabs and existing app versions.
  localStorage.setItem('ebr_token', token);
  localStorage.setItem('ebr_user', serializedUser);
}

function clearSession() {
  for (const storage of [sessionStorage, localStorage]) {
    storage.removeItem('ebr_token');
    storage.removeItem('ebr_user');
  }
}

function readUser() {
  let token = sessionStorage.getItem('ebr_token');
  if (!token) {
    token = localStorage.getItem('ebr_token');
    if (token) {
      // Migrate sessions created before tab-scoped storage was introduced.
      sessionStorage.setItem('ebr_token', token);
      const legacyUser = localStorage.getItem('ebr_user');
      if (legacyUser) sessionStorage.setItem('ebr_user', legacyUser);
    }
  }
  if (!token) {
    clearSession();
    return null;
  }
  try {
    return JSON.parse(sessionStorage.getItem('ebr_user') || localStorage.getItem('ebr_user') || 'null');
  } catch {
    clearSession();
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readUser);

  useEffect(() => {
    const expireSession = () => setUser(null);
    const syncTabSession = event => {
      if (event.key === 'ebr_token' && event.newValue) {
        sessionStorage.setItem('ebr_token', event.newValue);
        const sharedUser = localStorage.getItem('ebr_user');
        if (sharedUser) {
          sessionStorage.setItem('ebr_user', sharedUser);
          try { setUser(JSON.parse(sharedUser)); } catch { setUser(null); }
        }
      } else if (event.key === 'ebr_user' && event.newValue && sessionStorage.getItem('ebr_token')) {
        sessionStorage.setItem('ebr_user', event.newValue);
        try { setUser(JSON.parse(event.newValue)); } catch { setUser(null); }
      }
      // Ignore removals from other tabs; they must not invalidate this tab's token.
    };
    window.addEventListener('ebr:session-expired', expireSession);
    window.addEventListener('storage', syncTabSession);
    return () => {
      window.removeEventListener('ebr:session-expired', expireSession);
      window.removeEventListener('storage', syncTabSession);
    };
  }, []);

  async function login(email, password) {
    const { data } = await loginApi({ email, password });
    writeSession(data.token, data.user);
    setUser(data.user);
    return data.user;
  }

  function logout() {
    clearSession();
    setUser(null);
  }

  return <Context.Provider value={{ user, login, logout }}>{children}</Context.Provider>;
}

export const useAuth = () => useContext(Context);
