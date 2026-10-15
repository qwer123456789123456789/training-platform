<!-- src/layout/AdminLayout.vue —— 后台布局：侧边栏 + 顶栏 + 内容区 -->
<template>
  <el-container style="height: 100vh">
    <!-- 侧边栏 -->
    <el-aside width="200px" style="background: #001529">
      <div style="color: #fff; text-align: center; line-height: 60px; font-weight: 700">实训管理平台</div>
      <el-menu :default-active="$route.path" router background-color="#001529" text-color="#c0c4cc" active-text-color="#fff">
        <el-menu-item index="/admin/user">用户管理</el-menu-item>
        <el-menu-item index="/admin/class">班级管理</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏：下拉菜单（个人信息 / 退出登录） -->
      <el-header style="display: flex; align-items: center; justify-content: flex-end; border-bottom: 1px solid #eee">
        <el-dropdown @command="handleCommand">
          <span style="cursor: pointer; font-size: 14px">管理员：{{ realName }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 内容区 -->
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
const realName = localStorage.getItem('realName') || '管理员'

const handleCommand = (cmd) => {
  if (cmd === 'profile') {
    router.push('/admin/profile')
  }
  if (cmd === 'logout') {
    localStorage.clear()
    router.push('/login')
  }
}
</script>
