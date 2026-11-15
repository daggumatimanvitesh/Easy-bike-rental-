import axios from 'axios';
const localApiTarget = (import.meta.env.VITE_API_TARGET || 'http://127.0.0.1:8081').replace(/\/$/, '');
const apiBaseUrl = import.meta.env.DEV ? `${localApiTarget}/api` : (import.meta.env.VITE_API_URL || '/api');
const api = axios.create({ baseURL: apiBaseUrl });
const getSessionToken = () => sessionStorage.getItem('ebr_token') || localStorage.getItem('ebr_token');
api.interceptors.request.use(config => {
  const token = getSessionToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
export const getBikes = () => api.get('/bikes');
export const getBike = id => api.get(`/bikes/${id}`);
export const login = data => api.post('/auth/login', data);
export const register = data => api.post('/auth/register', data);
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
    const rejectedToken = ['TOKEN_REJECTED', 'TOKEN_USER_NOT_FOUND', 'USER_DISABLED'].includes(code);
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
