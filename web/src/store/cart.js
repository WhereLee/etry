import { defineStore } from 'pinia'
import { addToCart, fetchCart, removeFromCart } from '@/features/commerce/api/cart'
import { isLoggedIn } from '@/store/session'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    totalCents: 0,
    loaded: false
  }),
  getters: {
    count: (state) => state.items.length
  },
  actions: {
    async refresh() {
      if (!isLoggedIn()) {
        this.reset()
        return
      }
      const data = await fetchCart()
      this.items = data.items
      this.totalCents = data.totalCents
      this.loaded = true
    },
    async add(materialId) {
      await addToCart(materialId)
      await this.refresh()
    },
    async remove(materialId) {
      await removeFromCart(materialId)
      await this.refresh()
    },
    reset() {
      this.items = []
      this.totalCents = 0
      this.loaded = false
    }
  }
})