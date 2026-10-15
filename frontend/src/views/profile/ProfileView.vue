<!-- src/views/profile/ProfileView.vue —— 个人信息修改页 -->
<template>
  <el-card style="max-width: 560px">
    <template #header>个人信息</template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="账号">
        <el-input :model-value="form.username" disabled />
      </el-form-item>
      <el-form-item label="姓名" prop="realName">
        <el-input v-model="form.realName" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="角色">
        <el-input :model-value="roleText(form.role)" disabled />
      </el-form-item>
      <el-form-item label="班级ID">
        <el-input :model-value="form.classId || '无'" disabled />
      </el-form-item>
      <el-form-item label="新密码" prop="password">
        <el-input v-model="form.password" type="password" show-password placeholder="留空则不修改密码" />
      </el-form-item>
    </el-form>
    <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserById, updateUser } from '@/api/user'

const formRef = ref()
const saving = ref(false)
const form = reactive({ id: null, username: '', realName: '', role: '', classId: null, password: '' })

const rules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

const loadProfile = async () => {
  const userId = localStorage.getItem('userId')
  if (!userId) {
    ElMessage.warning('无法获取当前用户信息，请重新登录')
    return
  }
  const data = await getUserById(userId)
  Object.assign(form, {
    id: data.id, username: data.username, realName: data.realName,
    role: data.role, classId: data.classId, password: ''
  })
}

const handleSave = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { username: form.username, realName: form.realName, role: form.role, classId: form.classId }
      if (form.password) payload.password = form.password
      await updateUser(form.id, payload)
      if (form.realName) localStorage.setItem('realName', form.realName)
      ElMessage.success('保存成功')
    } catch (e) {
      // 错误信息由 request.js 统一弹出
    } finally {
      saving.value = false
    }
  })
}

const roleText = r => ({ student: '学生', teacher: '教师', admin: '管理员' }[r] || r)
onMounted(loadProfile)
</script>
