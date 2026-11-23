import { get, post, put, upload } from '@/shared/api/http'

export function listAdminMaterials(params) {
  return get('/admin/materials', params)
}

export function createAdminMaterial(body) {
  return post('/admin/materials', body)
}

export function updateAdminMaterial(id, body) {
  return put(`/admin/materials/${id}`, body)
}

export function setMaterialSaleStatus(id, saleStatus) {
  return put(`/admin/materials/${id}/sale-status`, { saleStatus })
}

export function setMaterialFeatured(id, featured) {
  return put(`/admin/materials/${id}/featured`, { featured })
}

export function uploadMaterialFile(id, file) {
  return upload(`/admin/materials/${id}/file`, file)
}