// src/router/index.js —— 路由 + 登录守卫
import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layout/AdminLayout.vue'
import LoginView from '@/views/login/LoginView.vue'

const routes = [
    { path: '/login', name: 'Login', component: LoginView },
    {
        path: '/admin',
        component: AdminLayout,
        redirect: '/admin/user',
        children: [
            { path: 'user',    name: 'UserManage',  component: () => import('@/views/admin/UserManage.vue'),  meta: { title: '用户管理' } },
            { path: 'class',   name: 'ClassManage', component: () => import('@/views/admin/ClassManage.vue'), meta: { title: '班级管理' } },
            { path: 'profile', name: 'Profile',     component: () => import('@/views/profile/ProfileView.vue'), meta: { title: '个人信息' } }
        ]
    },
    { path: '/', redirect: '/admin' }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 全局守卫：没 token 一律去登录页
router.beforeEach((to) => {
    const token = localStorage.getItem('token')
    if (to.path === '/login') {
        return token ? '/admin' : true
    }
    return token ? true : '/login'
})

export default router
