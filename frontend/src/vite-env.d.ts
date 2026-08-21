/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module '@form-create/designer'
declare module '@form-create/element-ui'
declare module '@form-create/utils/lib/unique'

