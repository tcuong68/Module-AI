# Đề cương đồ án

## 1. Tên đồ án

**Xây dựng Hệ thống Quản lý và Giới thiệu cho thuê phòng Thông minh tích hợp AI trên Nền tảng Website**

---

## 2. Nội dung chính đồ án

### a. Mục tiêu hệ thống

Xây dựng hệ thống quản lý phòng trọ trực tuyến giúp người cho thuê dễ dàng quản lý dãy trọ của mình, từ việc quản lý người thuê đến các chi phí phát sinh hàng tháng. Đồng thời, hệ thống kết nối và giới thiệu phòng trọ giữa người cho thuê và người thuê, giúp việc tìm kiếm phòng trọ trở nên dễ dàng và tiện nghi hơn.

### b. Chức năng chính

#### Người thuê
- Đặt lịch hẹn xem phòng.
- Đặt cọc.
- Quản lý hóa đơn và hợp đồng.
- Thanh toán tiền thuê trọ.
- Gia hạn hợp đồng.

#### Người cho thuê
- Đăng tin cho thuê.
- Quản lý người thuê.
- Quản lý hóa đơn và hợp đồng.
- Quản lý tài chính.
- Thống kê doanh thu.
- Thống kê tỷ lệ lấp đầy phòng.

#### Quản trị viên
- Xác thực độ tin cậy.
- Quản lý khiếu nại.
- Cấu hình hệ thống.
- Thống kê, báo cáo.
- Quản lý tri thức AI.

#### Hệ thống
- Chatbot hỗ trợ người dùng.
- Mô hình lọc tin đăng ảo.
- Tự động gửi Email/Thông báo.
- Tự động tính toán chi phí hàng tháng.
- Nhắc nhở thời gian hết hạn hợp đồng.
- Giới hạn số lượng truy cập để đảm bảo hệ thống hoạt động ổn định.

### c. Công nghệ sử dụng

#### Frontend
- ReactJS

#### Backend
- Spring Boot

#### AI
- **Large Language Model (LLM):** Sử dụng cho chatbot hỗ trợ người dùng.
- **Machine Learning (XGBoost):** Sử dụng cho chức năng lọc tin đăng ảo.
- **Content-Based Filtering kết hợp KNN:** Sử dụng để gợi ý phòng phù hợp dựa trên lịch sử xem phòng trước đó.

#### Database
- MySQL

#### Công cụ hỗ trợ
- Postman
- Docker
- Elasticsearch
- Kafka
- Redis
- Redis Insight

### d. Cơ sở dữ liệu ban đầu

Bao gồm các dữ liệu về:

- User
- Role
- Room
- Appointment
- Rating
- Comment
- Contract
- Notification
- Payment
