<!-- src/views/admin/UserManage.vue —— 用户管理页面 -->
<template>
  <el-card>
    <div style="margin-bottom: 14px; display: flex; gap: 10px">
      <el-input v-model="query.username" placeholder="按账号搜索" clearable style="width: 220px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button type="success" @click="openDialog()">新增用户</el-button>
    </div>

    <el-table :data="userList" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="账号" />
      <el-table-column prop="realName" label="姓名" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="roleTag(row.role)">{{ roleText(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="classId" label="班级ID" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="val => handleStatus(row, val)" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该用户？" @confirm="handleDelete(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
        style="margin-top: 14px; justify-content: flex-end"
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadList"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="账号"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserPage, addUser, updateUser, deleteUser } from '@/api/user'

const userList = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, username: '' })
const dialogVisible = ref(false)
const form = reactive({ id: null, username: '', password: '', realName: '', role: 'student' })

const loadList = async () => {
  const data = await getUserPage(query)
  userList.value = data.records
  total.value = data.total
}
const handleSearch = () => { query.current = 1; loadList() }

const openDialog = (row) => {
  Object.assign(form, row || { id: null, username: '', password: '', realName: '', role: 'student' })
  dialogVisible.value = true
}
const handleSave = async () => {
  if (form.id) await updateUser(form.id, form)
  else await addUser(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadList()
}
const handleStatus = async (row, val) => {
  await updateUser(row.id, { status: val ? 1 : 0 })
  ElMessage.success(val ? '已启用' : '已禁用')
  loadList()
}
const handleDelete = async (row) => {
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadList()
}
const roleText = r => ({ student: '学生', teacher: '教师', admin: '管理员' }[r] || r)
const roleTag = r => ({ student: '', teacher: 'warning', admin: 'danger' }[r] || '')

onMounted(loadList)
</script>
