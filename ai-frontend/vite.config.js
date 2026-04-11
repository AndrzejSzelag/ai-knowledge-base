import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        proxy: {

            // Endpointy REST
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
                // Brak rewrite = Spring dostaje pełną ścieżkę zaczynającą się od /api
            },

            // Wymagane dla Google OAuth2 redirect flow
            // Spring inicjuje flow: /oauth2/authorization/google
            '/oauth2': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            
            // Spring inicjuje flow: /oauth2/authorization/google` 
            '/login': {
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        }
    }
})