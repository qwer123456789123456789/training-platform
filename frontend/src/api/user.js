// src/api/user.js
import request from './request'
export const login        = (data) => request.post('/user/login', data)
export const getUserPage  = (params) => request.get('/user/page', { params })
export const getUserById  = (id)    => request.get(`/user/${id}`)
export const addUser      = (data)  => request.post('/user', data)
export const updateUser   = (id, data) => request.put(`/user/${id}`, data)
export const deleteUser   = (id)    => request.delete(`/user/${id}`)
