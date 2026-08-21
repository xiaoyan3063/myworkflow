import { useUserStore } from '@/stores/user'

export function hasPerm(perm: string) {
  return useUserStore().hasPerm(perm)
}
