// Logger utility - automatically disables console in production
const isDevelopment = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

export const logger = {
  log: (...args) => {
    if (isDevelopment) console.log(...args);
  },
  error: (...args) => {
    if (isDevelopment) console.error(...args);
  },
  warn: (...args) => {
    if (isDevelopment) console.warn(...args);
  },
  debug: (...args) => {
    if (isDevelopment) console.debug(...args);
  }
};
