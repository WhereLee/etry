<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { CATEGORY_LABELS, priceText } from '@/features/material/api/material'
import { checkoutCart } from '@/features/commerce/api/cart'
import { useCartStore } from '@/store/cart'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const cartStore = useCartStore()
const loading = ref(true)
const error = ref('')
const removingId = ref(null)
const checkingOut = ref(false)
const selected = ref([])

const selectableItems = computed(() => cartStore.items.filter((item) => !rowBlocked(item)))
const selectedItems = computed(() =>
  selectableItems.value.filter((item) => selected.value.includes(item.materialId))
)
const selectedTotal = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + (item.priceCents || 0), 0)
)
const allSelected = computed(() =>
  selectableItems.value.length > 0 && selectedItems.value.length === selectableItems.value.length
)

onMounted(async () => {
  try {
    await cartStore.refresh()
    selected.value = selectableItems.value.map((item) => item.materialId)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

async function remove(item) {
  removingId.value = item.materialId
  try {
    await cartStore.remove(item.materialId)
    selected.value = selected.value.filter((id) => id !== item.materialId)
    ElMessage.success('已移除')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    removingId.value = null
  }
}

function rowBlocked(item) {
  return item.purchased || !item.onSale
}

function toggleRow(item, checked) {
  if (checked) {
    if (!selected.value.includes(item.materialId)) selected.value.push(item.materialId)
  } else {
    selected.value = selected.value.filter((id) => id !== item.materialId)
  }
}

function toggleAll(checked) {
  selected.value = checked ? selectableItems.value.map((item) => item.materialId) : []
}

async function checkout() {
  if (!selectedItems.value.length || checkingOut.value) return
  checkingOut.value = true
  try {
    const order = await checkoutCart(
      selectedItems.value.map((item) => item.materialId),
      crypto.randomUUID()
    )
    ElMessage.success('订单已创建，请在 15 分钟内完成支付')
    await cartStore.refresh()
    selected.value = selectableItems.value.map((item) => item.materialId)
    router.push({ name: 'orderDetail', params: { orderNo: order.orderNo } })
  } catch (e) {
    ElMessage.error(e.message)
    try {
      await cartStore.refresh()
      selected.value = selectableItems.value.map((item) => item.materialId)
    } catch {
      // 刷新失败保留原列表，错误提示已给出
    }
  } finally {
    checkingOut.value = false
  }
}
</script>

<template>
  <main class="page cart-page">
    <PageHeader
      eyebrow="SHOPPING CART"
      title="购物车"
      description="电子资料每份限购一次。已购买或已下架的资料不计入合计。"
    />

    <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" class="feedback" />
    <el-skeleton v-if="loading" animated :rows="6" />

    <div v-else-if="!cartStore.items.length" class="empty-panel">
      <el-empty description="购物车还是空的">
        <el-button type="primary" @click="$router.push('/materials')">去逛逛资料商城</el-button>
      </el-empty>
    </div>

    <template v-else>
      <section class="cart-list">
        <article
          v-for="item in cartStore.items"
          :key="item.materialId"
          class="cart-row"
          :class="{ blocked: rowBlocked(item) }"
        >
          <el-checkbox
            class="row-check"
            :disabled="rowBlocked(item)"
            :model-value="selected.includes(item.materialId)"
            @change="(checked) => toggleRow(item, checked)"
          />
          <div class="row-main">
            <RouterLink :to="{ name: 'materialDetail', params: { id: item.materialId } }" class="row-title">
              {{ item.title }}
            </RouterLink>
            <div class="row-tags">
              <el-tag v-if="item.category" effect="plain" round size="small">
                {{ CATEGORY_LABELS[item.category] || item.category }}
              </el-tag>
              <el-tag v-if="item.purchased" type="success" effect="plain" round size="small">已购买</el-tag>
              <el-tag v-else-if="!item.onSale" type="info" effect="plain" round size="small">已下架</el-tag>
            </div>
          </div>
          <strong class="row-price">{{ priceText(item.priceCents, item.currency) }}</strong>
          <el-button
            text
            type="danger"
            :loading="removingId === item.materialId"
            @click="remove(item)"
          >
            移除
          </el-button>
        </article>
      </section>

      <footer class="cart-foot">
        <el-checkbox
          :model-value="allSelected"
          :indeterminate="selectedItems.length > 0 && !allSelected"
          :disabled="!selectableItems.length"
          @change="toggleAll"
        >
          全选
        </el-checkbox>
        <span class="foot-note">已选 {{ selectedItems.length }} 件</span>
        <strong class="foot-total">{{ priceText(selectedTotal) }}</strong>
        <el-button
          type="primary"
          size="large"
          :disabled="!selectedItems.length"
          :loading="checkingOut"
          @click="checkout"
        >
          结算
        </el-button>
      </footer>
    </template>
  </main>
</template>

<style scoped>
.feedback {
  margin-bottom: 18px;
}

.empty-panel {
  padding: 40px 0;
}

.cart-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cart-row {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.75);
}

.cart-row.blocked {
  opacity: 0.62;
}

.row-check {
  flex: none;
  margin-right: 2px;
}

.row-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.row-title {
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-title:hover {
  color: var(--pine);
}

.row-tags {
  display: flex;
  gap: 8px;
}

.row-price {
  flex: none;
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 18px;
}

.cart-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  margin-top: 20px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--pine-soft);
}

.foot-note {
  color: var(--muted);
  font-size: 13px;
}

.foot-total {
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 26px;
}
</style>