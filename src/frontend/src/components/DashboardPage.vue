<script setup>
import { ref, onMounted } from 'vue';
import { ElCard, ElTag } from 'element-plus';

const timeRange = ref('24h');
const mockData = {
  coreMetrics: {
    activeUsers: 128,
    sessionCount: 356,
    messageCount: 2847,
    sessionDepth: 8.2
  },
  aiPerformance: {
    successRate: 96.5,
    avgResponse: 850,
    p95Response: 1200,
    errorRate: 2.1,
    unknownRate: 5.3,
    slowRate: 1.8
  },
  operationEfficiency: {
    avgSessionPerUser: 2.8,
    avgMessagePerSession: 8.0,
    avgMessagePerUser: 22.2
  },
  trendData: {
    conversationTrend: [
      { time: '12:00', value: 12 },
      { time: '14:00', value: 28 },
      { time: '16:00', value: 45 },
      { time: '18:00', value: 38 },
      { time: '20:00', value: 52 },
      { time: '22:00', value: 35 },
      { time: '00:00', value: 18 },
      { time: '02:00', value: 8 },
      { time: '04:00', value: 5 },
      { time: '06:00', value: 12 },
      { time: '08:00', value: 25 },
      { time: '10:00', value: 42 }
    ],
    activeUserTrend: [
      { time: '12:00', value: 8 },
      { time: '14:00', value: 15 },
      { time: '16:00', value: 22 },
      { time: '18:00', value: 18 },
      { time: '20:00', value: 28 },
      { time: '22:00', value: 20 },
      { time: '00:00', value: 10 },
      { time: '02:00', value: 5 },
      { time: '04:00', value: 3 },
      { time: '06:00', value: 7 },
      { time: '08:00', value: 14 },
      { time: '10:00', value: 24 }
    ],
    responseTimeTrend: [
      { time: '12:00', value: 720 },
      { time: '14:00', value: 850 },
      { time: '16:00', value: 920 },
      { time: '18:00', value: 780 },
      { time: '20:00', value: 1050 },
      { time: '22:00', value: 890 },
      { time: '00:00', value: 650 },
      { time: '02:00', value: 580 },
      { time: '04:00', value: 520 },
      { time: '06:00', value: 680 },
      { time: '08:00', value: 750 },
      { time: '10:00', value: 900 }
    ],
    qualityTrend: {
      errorRate: [2.5, 2.1, 1.8, 2.3, 2.0, 1.9, 2.2, 2.8, 3.1, 2.5, 2.0, 2.1],
      unknownRate: [5.8, 5.3, 4.9, 5.5, 5.1, 4.8, 5.2, 6.0, 6.5, 5.8, 5.0, 5.3]
    }
  }
};

const formatNumber = (num) => {
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k';
  return num.toString();
};

const formatTime = (ms) => {
  if (ms >= 1000) return (ms / 1000).toFixed(1) + 's';
  return ms + 'ms';
};

const getMaxValue = (data) => {
  return Math.max(...data.map(d => d.value)) * 1.2;
};

const getBarHeight = (value, max) => {
  return max > 0 ? (value / max) * 100 : 0;
};

const getLinePoints = (data, width, height, max) => {
  if (data.length === 0) return '';
  const stepX = width / (data.length - 1);
  return data.map((d, i) => {
    const x = i * stepX;
    const y = height - (d.value / max) * height;
    return `${x},${y}`;
  }).join(' ');
};

const getAreaPoints = (data, width, height, max) => {
  if (data.length === 0) return '';
  const stepX = width / (data.length - 1);
  const points = data.map((d, i) => {
    const x = i * stepX;
    const y = height - (d.value / max) * height;
    return `${x},${y}`;
  });
  return `0,${height} ${points.join(' ')} ${width},${height}`;
};
</script>

<template>
  <div class="dashboard-page">
    <!-- 顶部控制栏 -->
    <div class="dashboard-header">
      <h1 class="page-title">Dashboard</h1>
      <div class="header-controls">
        <div class="time-range">
          <button :class="['time-btn', { active: timeRange === '24h' }]" @click="timeRange = '24h'">24h</button>
          <button :class="['time-btn', { active: timeRange === '7d' }]" @click="timeRange = '7d'">7d</button>
          <button :class="['time-btn', { active: timeRange === '30d' }]" @click="timeRange = '30d'">30d</button>
        </div>
        <span class="update-time">● 08/03 11:18:11</span>
        <button class="refresh-btn" title="刷新">🔄</button>
      </div>
    </div>

    <!-- 核心指标 -->
    <ElCard class="metrics-card" shadow="hover">
      <div class="card-title">核心指标</div>
      <div class="metrics-grid">
        <div class="metric-item">
          <div class="metric-value">{{ mockData.coreMetrics.activeUsers }}</div>
          <div class="metric-label">活跃用户</div>
          <div class="metric-icon" style="background: #dbeafe;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
          </div>
        </div>
        <div class="metric-item">
          <div class="metric-value">{{ mockData.coreMetrics.sessionCount }}</div>
          <div class="metric-label">会话数</div>
          <div class="metric-icon" style="background: #e0e7ff;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#6366f1" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
        </div>
        <div class="metric-item">
          <div class="metric-value">{{ formatNumber(mockData.coreMetrics.messageCount) }}</div>
          <div class="metric-label">消息数</div>
          <div class="metric-icon" style="background: #fef3c7;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
          </div>
        </div>
        <div class="metric-item">
          <div class="metric-value">{{ mockData.coreMetrics.sessionDepth }}</div>
          <div class="metric-label">会话深度（条/会话）</div>
          <div class="metric-icon" style="background: #dbeafe;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2">
              <line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>
            </svg>
          </div>
        </div>
      </div>
    </ElCard>

    <!-- 主内容区 -->
    <div class="dashboard-main">
      <!-- 左侧主区域 -->
      <div class="dashboard-left">
        <!-- 流量概览 -->
        <ElCard class="chart-card" shadow="hover">
          <div class="card-title">流量概览</div>
          <div class="chart-container">
            <svg viewBox="0 0 600 150" class="line-chart">
              <defs>
                <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.3"/>
                  <stop offset="100%" stop-color="#3b82f6" stop-opacity="0"/>
                </linearGradient>
              </defs>
              <!-- 网格线 -->
              <line x1="0" y1="37.5" x2="600" y2="37.5" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
              <line x1="0" y1="75" x2="600" y2="75" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
              <line x1="0" y1="112.5" x2="600" y2="112.5" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
              <!-- 面积 -->
              <polygon :points="getAreaPoints(mockData.trendData.conversationTrend, 600, 150, getMaxValue(mockData.trendData.conversationTrend))" fill="url(#areaGradient)"/>
              <!-- 折线 -->
              <polyline :points="getLinePoints(mockData.trendData.conversationTrend, 600, 150, getMaxValue(mockData.trendData.conversationTrend))" fill="none" stroke="#3b82f6" stroke-width="2"/>
              <!-- 数据点 -->
              <circle v-for="(d, i) in mockData.trendData.conversationTrend" :key="i"
                :cx="i * (600 / (mockData.trendData.conversationTrend.length - 1))"
                :cy="150 - (d.value / getMaxValue(mockData.trendData.conversationTrend)) * 150"
                r="3" fill="#3b82f6"/>
              <!-- X轴标签 -->
              <text v-for="(d, i) in mockData.trendData.conversationTrend.filter((_, idx) => idx % 2 === 0)" :key="'x' + i"
                :x="mockData.trendData.conversationTrend.indexOf(d) * (600 / (mockData.trendData.conversationTrend.length - 1))"
                y="145" text-anchor="middle" font-size="10" fill="#9ca3af">{{ d.time }}</text>
            </svg>
          </div>
        </ElCard>

        <!-- 趋势分析 -->
        <div class="trend-grid">
          <!-- 会话趋势 -->
          <ElCard class="trend-card" shadow="hover">
            <div class="card-title">会话趋势</div>
            <div class="card-subtitle">单位：次</div>
            <div class="chart-legend">
              <span class="legend-dot" style="background: #22c55e;"></span>
              <span>会话数</span>
            </div>
            <div class="chart-container small">
              <svg viewBox="0 0 280 120" class="line-chart">
                <line x1="0" y1="30" x2="280" y2="30" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <line x1="0" y1="60" x2="280" y2="60" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <line x1="0" y1="90" x2="280" y2="90" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <polyline :points="getLinePoints(mockData.trendData.conversationTrend, 280, 120, getMaxValue(mockData.trendData.conversationTrend))" fill="none" stroke="#22c55e" stroke-width="2"/>
                <circle v-for="(d, i) in mockData.trendData.conversationTrend" :key="i"
                  :cx="i * (280 / (mockData.trendData.conversationTrend.length - 1))"
                  :cy="120 - (d.value / getMaxValue(mockData.trendData.conversationTrend)) * 120"
                  r="2.5" fill="#22c55e"/>
              </svg>
            </div>
          </ElCard>

          <!-- 活跃用户趋势 -->
          <ElCard class="trend-card" shadow="hover">
            <div class="card-title">活跃用户趋势</div>
            <div class="card-subtitle">单位：人</div>
            <div class="chart-legend">
              <span class="legend-dot" style="background: #8b5cf6;"></span>
              <span>活跃用户</span>
            </div>
            <div class="chart-container small">
              <svg viewBox="0 0 280 120" class="line-chart">
                <line x1="0" y1="30" x2="280" y2="30" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <line x1="0" y1="60" x2="280" y2="60" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <line x1="0" y1="90" x2="280" y2="90" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <polyline :points="getLinePoints(mockData.trendData.activeUserTrend, 280, 120, getMaxValue(mockData.trendData.activeUserTrend))" fill="none" stroke="#8b5cf6" stroke-width="2"/>
                <circle v-for="(d, i) in mockData.trendData.activeUserTrend" :key="i"
                  :cx="i * (280 / (mockData.trendData.activeUserTrend.length - 1))"
                  :cy="120 - (d.value / getMaxValue(mockData.trendData.activeUserTrend)) * 120"
                  r="2.5" fill="#8b5cf6"/>
              </svg>
            </div>
          </ElCard>

          <!-- 响应时间趋势 -->
          <ElCard class="trend-card" shadow="hover">
            <div class="card-title">响应时间趋势</div>
            <div class="card-subtitle">单位：毫秒</div>
            <div class="chart-legend">
              <span class="legend-dot" style="background: #f59e0b;"></span>
              <span>平均响应时间</span>
            </div>
            <div class="chart-container small">
              <svg viewBox="0 0 280 120" class="line-chart">
                <line x1="0" y1="30" x2="280" y2="30" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <line x1="0" y1="60" x2="280" y2="60" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <line x1="0" y1="90" x2="280" y2="90" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <!-- 警告线 -->
                <line x1="0" y1="15" x2="280" y2="15" stroke="#ef4444" stroke-width="1" stroke-dasharray="4"/>
                <text x="275" y="12" text-anchor="end" font-size="8" fill="#ef4444">警告 >15s</text>
                <!-- 良好线 -->
                <line x1="0" y1="45" x2="280" y2="45" stroke="#3b82f6" stroke-width="1" stroke-dasharray="4"/>
                <text x="275" y="42" text-anchor="end" font-size="8" fill="#3b82f6">良好 ≤10s</text>
                <polyline :points="getLinePoints(mockData.trendData.responseTimeTrend, 280, 120, 1500)" fill="none" stroke="#f59e0b" stroke-width="2"/>
                <circle v-for="(d, i) in mockData.trendData.responseTimeTrend" :key="i"
                  :cx="i * (280 / (mockData.trendData.responseTimeTrend.length - 1))"
                  :cy="120 - (d.value / 1500) * 120"
                  r="2.5" fill="#f59e0b"/>
              </svg>
            </div>
          </ElCard>

          <!-- 质量趋势 -->
          <ElCard class="trend-card" shadow="hover">
            <div class="card-title">质量趋势</div>
            <div class="card-subtitle">单位：%</div>
            <div class="chart-legend">
              <span class="legend-dot" style="background: #ef4444;"></span>
              <span>错误率</span>
              <span class="legend-dot" style="background: #06b6d4; margin-left: 12px;"></span>
              <span>无知识率</span>
            </div>
            <div class="chart-container small">
              <svg viewBox="0 0 280 120" class="line-chart">
                <line x1="0" y1="30" x2="280" y2="30" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <line x1="0" y1="60" x2="280" y2="60" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <line x1="0" y1="90" x2="280" y2="90" stroke="#e5e7eb" stroke-width="1" stroke-dasharray="4"/>
                <!-- 无知识警告线 -->
                <line x1="0" y1="35" x2="280" y2="35" stroke="#ef4444" stroke-width="1" stroke-dasharray="4"/>
                <text x="275" y="32" text-anchor="end" font-size="8" fill="#ef4444">无知识警告</text>
                <!-- 错误警告线 -->
                <line x1="0" y1="85" x2="280" y2="85" stroke="#f59e0b" stroke-width="1" stroke-dasharray="4"/>
                <text x="275" y="82" text-anchor="end" font-size="8" fill="#f59e0b">错误警告</text>
                <polyline :points="getLinePoints(mockData.trendData.qualityTrend.errorRate.map((v, i) => ({value: v})), 280, 120, 10)" fill="none" stroke="#ef4444" stroke-width="2"/>
                <polyline :points="getLinePoints(mockData.trendData.qualityTrend.unknownRate.map((v, i) => ({value: v})), 280, 120, 10)" fill="none" stroke="#06b6d4" stroke-width="2"/>
              </svg>
            </div>
          </ElCard>
        </div>
      </div>

      <!-- 右侧边栏 -->
      <div class="dashboard-right">
        <!-- AI 性能 -->
        <ElCard class="side-card" shadow="hover">
          <div class="card-header">
            <span class="card-title">AI 性能</span>
            <ElTag size="small" type="info">暂无数据</ElTag>
          </div>
          <div class="performance-circle">
            <svg viewBox="0 0 120 120" class="circle-chart">
              <circle cx="60" cy="60" r="50" fill="none" stroke="#e5e7eb" stroke-width="8"/>
              <circle cx="60" cy="60" r="50" fill="none" stroke="#ef4444" stroke-width="8"
                :stroke-dasharray="`${mockData.aiPerformance.successRate * 3.14} 314`"
                stroke-linecap="round" transform="rotate(-90 60 60)"/>
              <text x="60" y="55" text-anchor="middle" font-size="20" font-weight="bold" fill="#ef4444">{{ mockData.aiPerformance.successRate }}%</text>
              <text x="60" y="72" text-anchor="middle" font-size="10" fill="#9ca3af">成功率</text>
            </svg>
          </div>
          <div class="performance-metrics">
            <div class="perf-item">
              <span class="perf-label">⏱ 平均响应</span>
              <span class="perf-value" style="color: #22c55e;">{{ formatTime(mockData.aiPerformance.avgResponse) }}</span>
            </div>
            <div class="perf-item">
              <span class="perf-label">⏱ P95 响应</span>
              <span class="perf-value" style="color: #22c55e;">{{ formatTime(mockData.aiPerformance.p95Response) }}</span>
            </div>
          </div>
          <div class="quality-bars">
            <div class="quality-title">质量快照（柱状）<span class="quality-subtitle">滚动 24h</span></div>
            <div class="bar-grid">
              <div class="bar-item">
                <div class="bar-container">
                  <div class="bar-fill" :style="{height: mockData.aiPerformance.errorRate + '%', background: '#ef4444'}"></div>
                </div>
                <div class="bar-label">{{ mockData.aiPerformance.errorRate }}%</div>
                <div class="bar-desc">错误率</div>
                <div class="bar-threshold">阈值 ≤5%</div>
              </div>
              <div class="bar-item">
                <div class="bar-container">
                  <div class="bar-fill" :style="{height: mockData.aiPerformance.unknownRate + '%', background: '#f59e0b'}"></div>
                </div>
                <div class="bar-label">{{ mockData.aiPerformance.unknownRate }}%</div>
                <div class="bar-desc">无知识率</div>
                <div class="bar-threshold">阈值 ≤20%</div>
              </div>
              <div class="bar-item">
                <div class="bar-container">
                  <div class="bar-fill" :style="{height: mockData.aiPerformance.slowRate + '%', background: '#3b82f6'}"></div>
                </div>
                <div class="bar-label">{{ mockData.aiPerformance.slowRate }}%</div>
                <div class="bar-desc">慢响应率</div>
                <div class="bar-threshold">阈值 ≤20%</div>
              </div>
            </div>
          </div>
        </ElCard>

        <!-- 运营效率 -->
        <ElCard class="side-card" shadow="hover">
          <div class="card-header">
            <span class="card-title">运营效率</span>
            <span class="card-subtitle">滚动 24h</span>
          </div>
          <div class="efficiency-list">
            <div class="efficiency-item">
              <span>人均会话</span>
              <span class="efficiency-value">{{ mockData.operationEfficiency.avgSessionPerUser }}</span>
            </div>
            <div class="efficiency-item">
              <span>单会话消息</span>
              <span class="efficiency-value">{{ mockData.operationEfficiency.avgMessagePerSession }}</span>
            </div>
            <div class="efficiency-item">
              <span>人均消息</span>
              <span class="efficiency-value">{{ mockData.operationEfficiency.avgMessagePerUser }}</span>
            </div>
          </div>
        </ElCard>

        <!-- 运营洞察 -->
        <ElCard class="side-card" shadow="hover">
          <div class="card-title">运营洞察</div>
          <div class="insight-item">
            <div class="insight-header">
              <span class="insight-tag">趋势</span>
              <span class="insight-time">11:18:11</span>
            </div>
            <div class="insight-content">
              <h4>暂无会话数据</h4>
              <p>Dashboard: 滚动 24h</p>
              <p class="insight-reason">归因：当前窗口内暂无消息记录，各项指标将会在会话产生后自动更新</p>
            </div>
          </div>
        </ElCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  padding: 0;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.time-range {
  display: flex;
  gap: 4px;
  background: #f1f5f9;
  border-radius: 8px;
  padding: 4px;
}

.time-btn {
  padding: 6px 12px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #64748b;
  transition: all 0.2s;
}

.time-btn.active {
  background: #1e293b;
  color: #ffffff;
}

.time-btn:hover:not(.active) {
  background: #e2e8f0;
}

.update-time {
  font-size: 13px;
  color: #64748b;
}

.refresh-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #e2e8f0;
  background: white;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.refresh-btn:hover {
  background: #f1f5f9;
}

/* 核心指标 */
.metrics-card {
  margin-bottom: 24px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 16px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.metric-item {
  background: #f8fafc;
  border-radius: 12px;
  padding: 20px;
  position: relative;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}

.metric-label {
  font-size: 13px;
  color: #64748b;
}

.metric-icon {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 主内容区 */
.dashboard-main {
  display: flex;
  gap: 24px;
}

.dashboard-left {
  flex: 1;
  min-width: 0;
}

.chart-card {
  margin-bottom: 24px;
}

.chart-container {
  height: 160px;
}

.chart-container.small {
  height: 140px;
}

.line-chart {
  width: 100%;
  height: 100%;
}

/* 趋势网格 */
.trend-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.trend-card {
  min-height: 200px;
}

.card-subtitle {
  font-size: 12px;
  color: #94a3b8;
  margin-top: -12px;
  margin-bottom: 8px;
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 12px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

/* 右侧边栏 */
.dashboard-right {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-card {
  flex-shrink: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-header .card-subtitle {
  margin: 0;
}

/* AI 性能 */
.performance-circle {
  display: flex;
  justify-content: center;
  margin: 20px 0;
}

.circle-chart {
  width: 120px;
  height: 120px;
}

.performance-metrics {
  border-top: 1px solid #e5e7eb;
  padding-top: 12px;
}

.perf-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.perf-label {
  font-size: 13px;
  color: #64748b;
}

.perf-value {
  font-size: 14px;
  font-weight: 600;
}

/* 质量柱状 */
.quality-bars {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.quality-title {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 12px;
}

.quality-subtitle {
  float: right;
  font-size: 11px;
  color: #94a3b8;
}

.bar-grid {
  display: flex;
  justify-content: space-around;
  gap: 12px;
}

.bar-item {
  text-align: center;
  flex: 1;
}

.bar-container {
  height: 60px;
  background: #f1f5f9;
  border-radius: 6px;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
  margin-bottom: 8px;
}

.bar-fill {
  width: 100%;
  border-radius: 6px;
  transition: height 0.3s;
  min-height: 2px;
}

.bar-label {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 2px;
}

.bar-desc {
  font-size: 11px;
  color: #64748b;
}

.bar-threshold {
  font-size: 10px;
  color: #94a3b8;
  margin-top: 2px;
}

/* 运营效率 */
.efficiency-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.efficiency-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 14px;
  color: #64748b;
}

.efficiency-value {
  font-weight: 600;
  color: #1e293b;
}

/* 运营洞察 */
.insight-item {
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.insight-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.insight-tag {
  background: #dbeafe;
  color: #3b82f6;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.insight-time {
  font-size: 12px;
  color: #94a3b8;
}

.insight-content h4 {
  font-size: 14px;
  color: #1e293b;
  margin: 0 0 4px 0;
}

.insight-content p {
  font-size: 12px;
  color: #64748b;
  margin: 2px 0;
}

.insight-reason {
  color: #94a3b8;
  font-size: 11px;
  margin-top: 8px;
}
</style>