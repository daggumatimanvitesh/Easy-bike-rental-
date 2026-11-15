import { createContext, useContext, useEffect, useState } from 'react';
import { login as loginApi } from '../services/api';

const Context = createContext(null);

function getSessionValue(key) {
  return sessionStorage.getItem(key) || localStorage.getItem(key);
}

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
  if (!getSessionValue('ebr_token')) {
    clearSession();
    return null;
  }
  try {
    return JSON.parse(getSessionValue('ebr_user') || 'null');
  } catch {
    clearSession();
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readUser);

  useEffect(() => {
    const expireSession = () => setUser(null);
    window.addEventListener('ebr:session-expired', expireSession);
    return () => window.removeEventListener('ebr:session-expired', expireSession);
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
