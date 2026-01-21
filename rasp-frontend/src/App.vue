<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue';

const alerts = ref([])
const timer = ref(null)
const raspEnabled = ref(true) // 默认开启
const targetIp = ref('')
const testLoading = ref(false)
const testResult = ref('')

// 表格列定义
const columns = [
  {
    title: '时间',
    dataIndex: 'time',
    key: 'time',
    width: 200,
  },
  {
    title: '类型',
    dataIndex: 'type',
    key: 'type',
    width: 100,
  },
  {
    title: '详情 Payload / 污点路径',
    dataIndex: 'detail',
    key: 'detail',
  },
  {
    title: '处理结果',
    dataIndex: 'status',
    key: 'status',
    width: 150,
  },
];

// 获取告警列表
const fetchAlerts = async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/rasp/alerts')
    alerts.value = res.data
  } catch (err) {
    console.error("Failed to fetch alerts", err)
  }
}

// 获取当前开关状态
const fetchConfig = async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/rasp/config')
    raspEnabled.value = res.data.enabled
  } catch (err) {
    console.error("Failed to fetch config", err)
  }
}

// 切换 RASP 开关
const toggleRasp = async (checked) => {
  try {
    // checked 是切换后的状态
    const res = await axios.post('http://localhost:8080/api/rasp/config/toggle', {
      enabled: checked
    })
    raspEnabled.value = res.data.enabled
    message.success(`RASP 防御已${checked ? '开启' : '关闭'}`);
  } catch (err) {
    console.error("Failed to toggle config", err)
    message.error("切换失败，请检查后端服务");
    // 回滚状态
    raspEnabled.value = !checked;
  }
}

// 执行连通性测试
const runNetworkTest = async () => {
  if (!targetIp.value) {
    message.warning('请输入 IP 地址');
    return;
  }
  
  testLoading.value = true;
  testResult.value = '';
  
  try {
    const res = await axios.post('http://localhost:8080/api/net/ping', {
      ip: targetIp.value
    });
    testResult.value = res.data;
    if (res.data.includes('RASP Blocked')) {
      message.error('检测到恶意攻击，已被 RASP 阻断！');
    } else {
      message.success('测试完成');
    }
  } catch (err) {
    console.error("Network test failed", err);
    message.error('请求失败');
  } finally {
    testLoading.value = false;
    // 立即刷新告警日志
    fetchAlerts();
  }
}

onMounted(() => {
  fetchConfig()
  fetchAlerts()
  // 每 2 秒轮询一次
  timer.value = setInterval(fetchAlerts, 2000)
})

onUnmounted(() => {
  if (timer.value) clearInterval(timer.value)
})
</script>

<template>
  <a-layout class="layout">
    <a-layout-header style="background: #fff; padding: 0 50px; border-bottom: 1px solid #f0f0f0;">
      <div class="header-content">
        <div class="logo">
          <a-typography-title :level="3" style="margin: 0;">RASP demo</a-typography-title>
        </div>
        
        <div class="control-panel">
          <span style="margin-right: 10px; font-weight: bold;">防御状态:</span>
          <a-switch 
            v-model:checked="raspEnabled" 
            checked-children="开启" 
            un-checked-children="关闭" 
            @change="toggleRasp"
          />
        </div>
      </div>
    </a-layout-header>

    <a-layout-content style="padding: 24px 50px;">
      
      <!-- 攻击模拟区域 -->
      <a-card title="网络连通性测试 (漏洞模拟)" style="margin-bottom: 24px;">
        <div style="display: flex; gap: 16px; margin-bottom: 16px;">
          <a-input 
            v-model:value="targetIp" 
            placeholder="请输入 IP 地址 (例如: 127.0.0.1)" 
            style="width: 400px;" 
            @pressEnter="runNetworkTest"
          />
          <a-button type="primary" :loading="testLoading" @click="runNetworkTest">
            开始测试
          </a-button>
        </div>
        
        <div v-if="testResult" class="console-output">
          <pre>{{ testResult }}</pre>
        </div>
      </a-card>

      <!-- 审计日志区域 -->
      <a-card title="安全审计日志">
        <a-table 
          :dataSource="alerts" 
          :columns="columns" 
          :pagination="{ pageSize: 10 }"
          rowKey="time"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'type'">
              <a-tag :color="record.type === 'RCE' ? 'red' : 'blue'">
                {{ record.type }}
              </a-tag>
            </template>
            
            <template v-if="column.key === 'detail'">
              <div class="detail-cell">
                <span style="font-family: monospace; white-space: pre-wrap;">{{ record.detail }}</span>
              </div>
            </template>

            <template v-if="column.key === 'status'">
              <a-tag v-if="record.status === 'BLOCKED'" color="error">已阻断</a-tag>
              <a-tag v-else-if="record.status === 'MONITORED'" color="warning">仅监控</a-tag>
              <a-tag v-else color="success">放行</a-tag>
            </template>
          </template>
        </a-table>
      </a-card>
    </a-layout-content>
    
    <a-layout-footer style="text-align: center">
      RASP Security Dashboard ©2024
    </a-layout-footer>
  </a-layout>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
}

.logo {
  display: flex;
  flex-direction: column;
  justify-content: center;
  line-height: 1.2;
}

.console-output {
  background-color: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 300px;
  overflow-y: auto;
}

.detail-cell {
  max-height: 150px;
  overflow-y: auto;
  font-size: 12px;
}
</style>
