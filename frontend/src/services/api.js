import axios from 'axios';
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api' });
api.interceptors.request.use(config => { const token = localStorage.getItem('ebr_token'); if (token) config.headers.Authorization = `Bearer ${token}`; return config; });
export const getBikes = () => api.get('/bikes');
export const getBike = id => api.get(`/bikes/${id}`);
export const login = data => api.post('/auth/login', data);
export const register = data => api.post('/auth/register', data);
export const getBookings = () => api.get('/bookings');
export const createBooking = data => api.post('/bookings', data);
export const createRazorpayOrder = bookingId => api.post('/payments/orders', { bookingId });
export const verifyRazorpayPayment = data => api.post('/payments/verify', data);
api.interceptors.response.use(response => response, error => {
  const status = error.response?.status;
  const url = error.config?.url || '';
  const protectedBookingRequest = /^\/(bookings|payments\/(orders|verify))(\/|\?|$)/.test(url);
  if (status === 401 && protectedBookingRequest) {
    localStorage.removeItem('ebr_token');
    localStorage.removeItem('ebr_user');
    window.dispatchEvent(new Event('ebr:session-expired'));
  }
  return Promise.reject(error);
});
export default api;
