// src/api/class.js
import request from './request'
export const getClassPage = (params) => request.get('/class/page', { params })
export const getClassById = (id)    => request.get(`/class/${id}`)
export const addClass     = (data)  => request.post('/class', data)
export const updateClass  = (id, data) => request.put(`/class/${id}`, data)
export const deleteClass  = (id)    => request.delete(`/class/${id}`)
