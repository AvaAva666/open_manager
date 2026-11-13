package com.automanager.dashboard;
import com.automanager.dashboard.DashboardInfo;

interface IDashboardService {
    /** 创建自定义仪表View，添加到WindowManager悬浮窗 */
    void dbShow(String vehicleModel);
    /** 移除WindowManager中的仪表View */
    void dbHide();
    /** 打开仪表配置 **/
    void dbOpenConfig();

    /** 获取服务仪表盘信息 */
    DashboardInfo dbInfo();
}