// src/api/request.js —— 统一处理返回体 {code,msg,data} 和 token
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({ baseURL: '/api', timeout: 10000 })

// 请求拦截：自动携带 token
request.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) config.headers['Authorization'] = 'Bearer ' + token
    return config
})

// 响应拦截：code=200 时直接返回 data；401 跳登录；其他弹 msg
request.interceptors.response.use(
    res => {
        const { code, msg, data } = res.data
        if (code === 200) return data
        if (code === 401) {
            ElMessage.warning('登录已失效，请重新登录')
            localStorage.removeItem('token')
            router.push('/login')
            return Promise.reject(new Error(msg))
        }
        ElMessage.error(msg || '操作失败')
        return Promise.reject(new Error(msg))
    },
    err => {
        ElMessage.error('网络异常，请稍后重试')
        return Promise.reject(err)
    }
)
export default request
