import { createContext, useContext, useState } from 'react';
import { login as loginApi } from '../services/api';
const Context = createContext(null);
export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => JSON.parse(localStorage.getItem('ebr_user') || 'null'));
  async function login(email, password) { const { data } = await loginApi({ email, password }); localStorage.setItem('ebr_token', data.token); localStorage.setItem('ebr_user', JSON.stringify(data.user)); setUser(data.user); return data.user; }
  function logout() { localStorage.removeItem('ebr_token'); localStorage.removeItem('ebr_user'); setUser(null); }
  return <Context.Provider value={{ user, login, logout }}>{children}</Context.Provider>;
}
export const useAuth = () => useContext(Context);
