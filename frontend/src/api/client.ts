import axios from 'axios';

const API_URL = 'http://localhost:8080/api/v1';

const client = axios.create({
    baseURL: API_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Add a request interceptor to inject the token
client.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('pickleball_auth_token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Add a response interceptor to handle errors
client.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        if (error.response && error.response.status === 401) {
            // Optional: Redirect to login or clear token
            // localStorage.removeItem('pickleball_auth_token');
            // window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

export default client;
