-- Fix password hash for demo accounts to match "password123" with Spring Security BCrypt
UPDATE users 
SET password_hash = '$2a$10$XwkQbphlW.QbP9QQYlTNw.pqVXuZRgD6EdWeE5DTtlTjFWyA9NPPy';
