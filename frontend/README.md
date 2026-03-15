# Secure P2P Wallet Frontend

React + TypeScript + Vite frontend for the Spring Boot backend in this repository.

## Stack

- React + TypeScript + Vite
- Tailwind CSS
- shadcn/ui-style component primitives
- React Query
- Axios
- React Hook Form + Zod

## Scalable architecture

```text
src/
  components/
    dashboard/
    navigation/
    ui/
  services/
  hooks/
  layouts/
  utils/
  pages/
    Dashboard/
      index.tsx
    Login/
      index.tsx
    Register/
      index.tsx
  auth/
  types/
```

## API integration (preserved)

The backend contracts remain unchanged:

- `POST /auth/register`
- `POST /auth/login`
- `GET /wallets/me`
- `POST /wallets/me`
- `POST /transactions/transfer`
- `GET /transactions/history`

## Run locally

```bash
cp .env.example .env
npm install
npm run dev
```

Default frontend URL: `http://localhost:4173`  
Default frontend API base URL: `/api`  
Default dev proxy target: `http://localhost:8080`
