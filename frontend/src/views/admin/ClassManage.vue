<!-- src/views/admin/ClassManage.vue —— 班级管理页面 -->
<template>
  <el-card>
    <div style="margin-bottom: 14px; display: flex; gap: 10px">
      <el-input v-model="query.className" placeholder="按班级名称搜索" clearable style="width: 220px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button type="success" @click="openDialog()">新增班级</el-button>
    </div>

    <el-table :data="classList" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="className" label="班级名称" />
      <el-table-column prop="teacherId" label="负责教师ID" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该班级？" @confirm="handleDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑班级' : '新增班级'" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="班级名称"><el-input v-model="form.className" /></el-form-item>
        <el-form-item label="负责教师">
          <el-select v-model="form.teacherId" style="width: 100%">
            <el-option v-for="t in teacherList" :key="t.id" :label="t.realName" :value="t.id" />
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
import { getClassPage, addClass, updateClass, deleteClass } from '@/api/class'
import { getUserPage } from '@/api/user'

const classList = ref([])
const teacherList = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, className: '' })
const dialogVisible = ref(false)
const form = reactive({ id: null, className: '', teacherId: null })

const loadList = async () => {
  const data = await getClassPage(query)
  classList.value = data.records
  total.value = data.total
}
const loadTeachers = async () => {
  const data = await getUserPage({ current: 1, size: 100 })
  teacherList.value = (data.records || []).filter(u => u.role === 'teacher')
}
const handleSearch = () => { query.current = 1; loadList() }

const openDialog = (row) => {
  Object.assign(form, row || { id: null, className: '', teacherId: null })
  dialogVisible.value = true
}
const handleSave = async () => {
  if (form.id) await updateClass(form.id, form)
  else await addClass(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadList()
}
const handleDelete = async (row) => {
  await deleteClass(row.id)
  ElMessage.success('删除成功')
  loadList()
}

onMounted(() => { loadList(); loadTeachers() })
</script>
