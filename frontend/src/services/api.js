import axios from 'axios';
const apiBaseUrl = import.meta.env.DEV ? '/api' : (import.meta.env.VITE_API_URL || '/api');
const api = axios.create({ baseURL: apiBaseUrl });
const getSessionToken = () => {
  const tabToken = sessionStorage.getItem('ebr_token');
  if (tabToken) return tabToken;
  const legacyToken = localStorage.getItem('ebr_token');
  if (legacyToken) {
    sessionStorage.setItem('ebr_token', legacyToken);
    const legacyUser = localStorage.getItem('ebr_user');
    if (legacyUser) sessionStorage.setItem('ebr_user', legacyUser);
  }
  return legacyToken;
};
api.interceptors.request.use(config => {
  const token = getSessionToken();
  if (token) {
    const authorization = `Bearer ${token}`;
    if (typeof config.headers?.set === 'function') config.headers.set('Authorization', authorization);
    else config.headers = { ...config.headers, Authorization: authorization };
  }
  return config;
});
export const getBikes = () => api.get('/bikes');
export const getBike = id => api.get(`/bikes/${id}`);
export const login = data => api.post('/auth/login', data);
export const register = data => api.post('/auth/register', data);
export const verifyEmail = token => api.post('/auth/verify-email', { token });
export const resendVerification = email => api.post('/auth/resend-verification', { email });
export const getBookings = () => api.get('/bookings');
export const createBooking = data => api.post('/bookings', data);
export const createRazorpayOrder = bookingId => api.post('/payments/orders', { bookingId });
export const verifyRazorpayPayment = data => api.post('/payments/verify', data);
export const simulateTestPayment = bookingId => api.post('/payments/demo', { bookingId });
api.interceptors.response.use(response => response, error => {
  const status = error.response?.status;
  const url = error.config?.url || '';
  const protectedBookingRequest = /^\/(bookings|payments\/(orders|verify|demo))(\/|\?|$)/.test(url);
  if (status === 401 && protectedBookingRequest) {
    const config = error.config;
    const currentToken = getSessionToken();
    const authorization = typeof config?.headers?.get === 'function'
      ? config.headers.get('Authorization')
      : (config?.headers?.Authorization || config?.headers?.authorization);
    const requestToken = typeof authorization === 'string' && authorization.startsWith('Bearer ')
      ? authorization.slice(7)
      : null;
    if (currentToken && config && !config.__ebrAuthRetried) {
      config.__ebrAuthRetried = true;
      if (typeof config.headers?.set === 'function') config.headers.set('Authorization', `Bearer ${currentToken}`);
      else config.headers = { ...config.headers, Authorization: `Bearer ${currentToken}` };
      return api.request(config);
    }
    const code = error.response?.data?.code;
    // AUTH_REQUIRED means the backend saw a bearer header but could not build an
    // authenticated SecurityContext. Reusing that same token forever leaves the
    // customer stuck on the booking/payment screen, so expire this tab's session.
    const rejectedToken = ['TOKEN_REJECTED', 'TOKEN_USER_NOT_FOUND', 'USER_DISABLED', 'EMAIL_NOT_VERIFIED', 'AUTH_REQUIRED'].includes(code);
    if (currentToken && requestToken === currentToken && rejectedToken) {
      sessionStorage.removeItem('ebr_token');
      sessionStorage.removeItem('ebr_user');
      localStorage.removeItem('ebr_token');
      localStorage.removeItem('ebr_user');
      window.dispatchEvent(new Event('ebr:session-expired'));
    }
  }
  return Promise.reject(error);
});
export default api;
