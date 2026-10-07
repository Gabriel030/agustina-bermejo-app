import js from '@eslint/js'
import globals from 'globals'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

const FEATURES = [
  'identity',
  'academic-structure',
  'enrollment',
  'attendance',
  'evaluation',
  'billing',
  'graduation',
  'announcements',
  'notifications',
  'workspace',
  'gestion-personas',
  'gestion-academica',
  'oferta-academica',
  'avisos',
]

const boundaryMessage = 'Una feature solo puede importar de otra a través de su index.js.'

const featureBoundaryRules = FEATURES.map((feature) => ({
  files: [`src/features/${feature}/**/*.{js,jsx}`],
  ignores: ['src/features/*/routes.jsx'],
  rules: {
    'no-restricted-imports': [
      'warn',
      {
        patterns: [
          {
            group: ['@/features/*/**', `!@/features/${feature}/**`, '!@/features/*/index', '!@/features/*/index.js'],
            message: boundaryMessage,
          },
        ],
      },
    ],
  },
}))

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      react.configs.flat.recommended,
      reactHooks.configs['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },
        sourceType: 'module',
      },
    },
    settings: {
      react: { version: 'detect' },
    },
    rules: {
      'no-unused-vars': ['error', { varsIgnorePattern: '^[A-Z_]' }],
      // React 19 con el runtime automático de JSX no requiere "React" en scope.
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
    },
  },
  {
    // Las primitivas de shadcn/ui (generadas, no editadas a mano) exportan
    // variantes/constantes junto al componente — patrón estándar de shadcn,
    // no aplica la regla de fast-refresh de un-solo-export-de-componente.
    files: ['src/components/ui/**/*.jsx', 'src/context/**/*.jsx'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
  {
    files: ['src/features/*/routes.jsx', 'src/routes/*.jsx'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
  ...featureBoundaryRules,
  {
    files: ['src/components/**/*.{js,jsx}', 'src/lib/**/*.{js,jsx}', 'src/layouts/**/*.{js,jsx}'],
    ignores: ['**/*.test.{js,jsx}'],
    rules: {
      'no-restricted-imports': [
        'warn',
        {
          patterns: [
            {
              group: ['@/features/**'],
              message: 'components, lib y layouts no pueden importar de features.',
            },
          ],
        },
      ],
    },
  },
])
