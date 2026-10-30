<template>
  <div class="page">
    <h1>🚄 购票系统</h1>

    <!-- ==================== 登录卡片(未登录) ==================== -->
    <el-card v-if="!token" class="card" style="max-width: 420px">
      <h3>登录</h3>
      <el-input v-model="username" placeholder="用户名" style="margin-bottom: 10px" />
      <el-input v-model="password" type="password" placeholder="密码" style="margin-bottom: 10px" />
      <el-button type="primary" :loading="loading" @click="login">登录</el-button>
      <el-button @click="register">注册</el-button>
    </el-card>

    <!-- ==================== 购票主流程(已登录) ==================== -->
    <template v-else>
      <!-- 常用乘车人管理(账号下储存,下单时勾选) -->
      <el-card class="card">
        <h3>👤 常用乘车人({{ passengers.length }} 人)</h3>
        <div class="row" style="margin-bottom: 12px">
          <el-input v-model="newPassengerName" placeholder="姓名" style="width: 150px" />
          <el-input v-model="newPassengerIdCard" placeholder="证件号(18位)" style="width: 220px" />
          <el-button type="primary" :loading="addingPassenger" @click="addPassenger">添加乘车人</el-button>
        </div>
        <el-table :data="passengers" size="small">
          <el-table-column prop="name" label="姓名" />
          <el-table-column prop="idCard" label="证件号" />
          <el-table-column label="操作" width="80">
            <template #default="scope">
              <el-button size="small" type="danger" link @click="removePassenger(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="card">
        <h3>查询车次</h3>
        <div class="row">
          <el-select v-model="fromStationId" placeholder="出发站" style="width: 160px">
            <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <span>→</span>
          <el-select v-model="toStationId" placeholder="到达站" style="width: 160px">
            <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <el-date-picker v-model="trainDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="乘车日期" style="width: 160px" />
          <el-button type="primary" @click="searchTrains">查询</el-button>
        </div>
      </el-card>

      <!-- 车次列表 -->
      <el-card v-if="trains.length" class="card">
        <h3>车次列表(点击选择)</h3>
        <el-table :data="trains" @row-click="selectTrain" highlight-current-row>
          <el-table-column prop="trainNo" label="车次" width="100" />
          <el-table-column prop="startStationName" label="出发站" />
          <el-table-column prop="endStationName" label="到达站" />
          <el-table-column prop="departTime" label="出发时间" width="110" />
          <el-table-column prop="arriveTime" label="到达时间" width="110" />
        </el-table>
      </el-card>

      <!-- 余票 + 选乘车人 + 下单 -->
      <el-card v-if="selectedTrain" class="card">
        <h3>{{ selectedTrain.trainNo }} {{ selectedTrain.startStationName }} → {{ selectedTrain.endStationName }}</h3>
        <el-table :data="stockRows" @row-click="selectStock" highlight-current-row>
          <el-table-column prop="seatTypeName" label="座位类型" width="140" />
          <el-table-column prop="available" label="余票" width="120" />
          <el-table-column prop="totalSeat" label="总票额" width="120" />
        </el-table>
        <!-- 勾选乘车人:票数 = 人数(后端按 N 人扣 N 张票) -->
        <el-table v-if="passengers.length" ref="passengerTableRef" :data="passengers"
                  @selection-change="onPassengerSelected" style="margin-top: 12px">
          <el-table-column type="selection" width="50" :selectable="canSelectPassenger" />
          <el-table-column prop="name" label="乘车人" />
          <el-table-column prop="idCard" label="证件号" />
        </el-table>
        <p v-else style="margin-top: 12px; color: #999">请先在上方添加乘车人</p>
        <div class="row" style="margin-top: 12px">
          <span>已选 <b>{{ selectedPassengers.length }}</b> 人,共 <b>{{ selectedPassengers.length }}</b> 张票</span>
          <el-button type="success" :loading="ordering" @click="createOrder">下单</el-button>
        </div>
      </el-card>

      <!-- 排队状态(轮询中) -->
      <el-card v-if="polling" class="card">
        <h3>排队中,请稍候…(每 2 秒自动查询)</h3>
        <el-progress :percentage="100" :indeterminate="true" :duration="2" />
      </el-card>

      <!-- 下单结果 -->
      <el-card v-if="orderResult" class="card">
        <template v-if="orderResult.success">
          <h3>✅ 购票成功!订单号:{{ orderResult.orderId }}</h3>
          <p>乘车人:{{ orderResult.passengerNames }}</p>
          <el-button type="primary" @click="pay">去支付</el-button>
        </template>
        <template v-else>
          <h3>❌ 下单失败:{{ orderResult.failReason }}</h3>
          <el-button @click="resetOrder">重新选择</el-button>
        </template>
      </el-card>

      <!-- 支付结果 -->
      <el-card v-if="payNo" class="card">
        <h3>💰 支付成功!支付单号:{{ payNo }}</h3>
      </el-card>
    </template>

    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import http from "./api.js";

// ---------- 状态(≈ Java 字段,改了视图自动更新) ----------
const token = ref(localStorage.getItem("token") || "");
const username = ref("");
const password = ref("");
const loading = ref(false);
const error = ref("");

const stations = ref([]);
const fromStationId = ref(null);
const toStationId = ref(null);
const trainDate = ref("");
const trains = ref([]);

// 乘车人
const passengers = ref([]);
const newPassengerName = ref("");
const newPassengerIdCard = ref("");
const addingPassenger = ref(false);

const selectedTrain = ref(null);
const stockRows = ref([]);
const selectedStock = ref(null);
const selectedPassengers = ref([]);
const passengerTableRef = ref(null);
const ordering = ref(false);

const polling = ref(false);
const orderResult = ref(null); // {success, orderId?, passengerNames?, failReason?}
const payNo = ref("");

// ---------- 方法 ----------
async function login() {
  loading.value = true;
  try {
    const data = await http.post("/user/login", {
      username: username.value,
      password: password.value,
    });
    token.value = data.token;
    localStorage.setItem("token", data.token);
    ElMessage.success("登录成功");
    loadStations();
    loadPassengers();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function register() {
  try {
    await http.post("/user/register", {
      username: username.value,
      password: password.value,
    });
    ElMessage.success("注册成功,请登录");
  } catch (e) {
    error.value = e.message;
  }
}

async function loadStations() {
  stations.value = await http.get("/train/stations");
}

async function loadPassengers() {
  passengers.value = await http.get("/user/passenger/list");
}

async function addPassenger() {
  if (!newPassengerName.value || !newPassengerIdCard.value) {
    ElMessage.warning("请填写姓名和证件号");
    return;
  }
  addingPassenger.value = true;
  try {
    await http.post("/user/passenger", {
      name: newPassengerName.value,
      idCard: newPassengerIdCard.value,
    });
    ElMessage.success("已添加");
    newPassengerName.value = "";
    newPassengerIdCard.value = "";
    loadPassengers();
  } catch (e) {
    error.value = e.message;
  } finally {
    addingPassenger.value = false;
  }
}

async function removePassenger(row) {
  try {
    await ElMessageBox.confirm(`确定删除乘车人「${row.name}」?`, "提示", { type: "warning" });
  } catch {
    return; // 用户点了取消
  }
  try {
    await http.delete(`/user/passenger/${row.id}`);
    ElMessage.success("已删除");
    loadPassengers();
  } catch (e) {
    error.value = e.message;
  }
}

async function searchTrains() {
  if (!fromStationId.value || !toStationId.value || !trainDate.value) {
    ElMessage.warning("请选择出发站、到达站和日期");
    return;
  }
  trains.value = await http.get("/train/search", {
    params: { from: fromStationId.value, to: toStationId.value },
  });
  if (!trains.value.length) ElMessage.info("没有符合条件的车次");
}

async function selectTrain(row) {
  selectedTrain.value = row;
  // 12306 语义:查询「出发站→到达站」这一个区间的各座位类型余票(后端过滤后返回 3 行)
  stockRows.value = await http.get("/ticket/stock", {
    params: {
      trainId: row.trainId,
      date: trainDate.value,
      fromStationId: fromStationId.value,
      toStationId: toStationId.value,
    },
  });
  selectedStock.value = null;
}

function selectStock(row) {
  selectedStock.value = row;
}

// 勾选上限 5 人:已达 5 人时,只有取消勾选可用(已选中的行允许反选)
function canSelectPassenger(row) {
  return selectedPassengers.value.length < 5 || selectedPassengers.value.includes(row);
}

function onPassengerSelected(rows) {
  selectedPassengers.value = rows;
}

async function createOrder() {
  if (!selectedStock.value) {
    ElMessage.warning("请先选择座位类型(点击余票行)");
    return;
  }
  if (!selectedPassengers.value.length) {
    ElMessage.warning("请至少勾选 1 名乘车人(最多 5 人)");
    return;
  }
  ordering.value = true;
  try {
    // 下单 → 拿排队号(requestId);票数 = 乘车人数,后端按 N 人扣 N 张票
    const requestId = await http.post("/order/create", {
      trainId: selectedTrain.value.trainId,
      trainDate: trainDate.value,
      seatTypeId: selectedStock.value.seatTypeId,
      fromStationId: fromStationId.value,
      toStationId: toStationId.value,
      passengers: selectedPassengers.value.map((p) => ({ name: p.name, idCard: p.idCard })),
    });
    // 轮询排队状态(阶段五的排队页:0排队中 1成功 2失败)
    polling.value = true;
    const timer = setInterval(async () => {
      const record = await http.get("/order/status", { params: { requestId } });
      if (record.status === 1) {
        clearInterval(timer);
        polling.value = false;
        orderResult.value = {
          success: true,
          orderId: record.orderId,
          passengerNames: selectedPassengers.value.map((p) => p.name).join("、"),
        };
        ElMessage.success("购票成功");
      } else if (record.status === 2) {
        clearInterval(timer);
        polling.value = false;
        orderResult.value = { success: false, failReason: record.failReason };
      }
    }, 2000);
  } catch (e) {
    error.value = e.message;
  } finally {
    ordering.value = false;
  }
}

async function pay() {
  try {
    payNo.value = await http.post("/pay/pay", null, {
      params: { orderId: orderResult.value.orderId },
    });
    ElMessage.success("支付完成");
  } catch (e) {
    error.value = e.message;
  }
}

function resetOrder() {
  orderResult.value = null;
  selectedStock.value = null;
  passengerTableRef.value?.clearSelection(); // 清掉勾选,重新选人
}

// ---------- 生命周期:已登录则直接加载车站和乘车人 ----------
onMounted(() => {
  if (token.value) {
    loadStations();
    loadPassengers();
  }
});
</script>

<style>
.page {
  max-width: 900px;
  margin: 20px auto;
  font-family: system-ui, sans-serif;
}
.card {
  margin-bottom: 16px;
}
.row {
  display: flex;
  gap: 10px;
  align-items: center;
}
.error {
  color: #f56c6c;
}
</style>
