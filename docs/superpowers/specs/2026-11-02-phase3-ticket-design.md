# Dormly Phase 3 — Thiết kế module Ticket

**Ngày:** 02/11/2026

**Trạng thái:** Đã qua builder check độc lập; bàn giao người dùng xem xét, chưa phê duyệt triển khai.

**Phạm vi:** nâng cấp module ticket hiện có, gồm xác nhận kết quả, xử lý lại, xét mở lại, SLA, lịch sử và thông báo.

**Nguồn quyết định:** người dùng xác nhận Admin và Staff đều thuộc BQL KTX; giao agent tự quyết các chi tiết còn lại và viết spec cho module ticket trước. Những lựa chọn bên dưới là quyết định thiết kế của agent theo ủy quyền, không phải chính sách đã được KTX xác thực.

## 1. Mục tiêu và giới hạn

Sinh viên gửi yêu cầu, biết BQL đã nhận và ai đang xử lý, kiểm tra kết quả rồi xác nhận hoàn tất hoặc phản hồi vấn đề còn tồn tại. BQL quản lý được việc chưa nhận, việc đang xử lý, việc chờ sinh viên và việc quá hạn. Mỗi lần xử lý phải có lịch sử để không đánh đồng “nhân viên báo xong” với “sinh viên xác nhận xong”.

### Trong phạm vi bắt buộc

- Giữ tạo ticket, comment công khai/nội bộ, tệp đính kèm, danh sách và Kanban hiện có.
- Làm rõ quyền Admin, Staff, người gửi; Staff nhận việc hoặc được Admin phân công.
- Báo đã xử lý với nội dung có ý nghĩa; sinh viên xác nhận hoặc yêu cầu tiếp tục xử lý.
- Tự đóng sau 72 giờ chờ xác nhận, nhắc trước ở giờ thứ 48.
- Gửi yêu cầu xét mở lại trong 7 ngày sau đóng; Admin chấp thuận hoặc từ chối có lý do.
- SLA phản hồi đầu tiên và xử lý, tính theo giờ liên tục; nhắc đúng người.
- Lịch sử nghiệp vụ, dữ liệu từng vòng xử lý, bảo vệ khi thao tác đồng thời/thử lại.
- API và dữ liệu đủ cho frontend; số liệu ticket cơ bản có thể đối chiếu.

### Ngoài phạm vi đợt này

Lịch hẹn sửa chữa; escalation nhiều tầng; incident; dashboard BI tổng thể; AI; thanh toán; đổi/phân phòng; phân quyền theo tòa/tầng; phân ca hoặc lịch nghỉ lễ; gộp ticket; đánh giá sao; sửa/xóa comment; xóa ticket. Các P0 hóa đơn và phân phòng của Phase 3 là công việc riêng, không được coi đã giải quyết bởi spec này.

Áp dụng quy trình chung cho tất cả category hiện có. Ticket `BILLING` chỉ là yêu cầu hỗ trợ, không được cập nhật trạng thái hóa đơn. Ticket `SECURITY` không thay thế kênh liên hệ khẩn cấp của KTX. Không thêm role kỹ thuật riêng.

### Kết quả không chấp nhận

Sinh viên bị đóng yêu cầu mà không biết vì sao; Staff thay đổi ticket ngoài quyền; URL tệp hoặc WebSocket làm lộ ghi chú nội bộ; mở lại làm mất lần xử lý trước; đổi hạn che mất vi phạm; thông báo báo thành công dù transaction thất bại; dùng dữ liệu minh họa như KPI thật.

## 2. Cơ sở hiện trạng và độ chắc chắn

Nguồn tham chiếu:

- Đề xuất `/home/cang/Downloads/de_xuat_phase3_dormly.md`, đặc biệt mục 5, 6, 8 và 14.3.
- [API ticket hiện tại](../../api/ticket-system.md).
- [Thiết kế ticket ban đầu](2026-07-23-ticket-support-system-design.md).
- Các phần mã đã đọc trong cuộc thảo luận, tại commit nền `acbba9b`.

Backend đã có `TicketMeController`, `TicketAdminService`, `TicketStatus`, `TicketOverdueScheduler`, comment, attachment và notification. Tài liệu API mô tả 5 trạng thái, 4 priority, phân công nhiều người, hạn `LocalDate`, role `ADMIN`/`STAFF` và API tách người gửi/BQL. Luồng hiện tại để BQL đổi trạng thái, sinh viên chủ yếu trao đổi qua comment. Mã đã đối chiếu cho thấy quay về xử lý xóa `resolvedAt`; thay đổi mới cần giữ lịch sử độc lập.

Tài liệu cũ và mã có thể khác nhau, ví dụ yêu cầu resolution note và cách trả lỗi. Các mô tả cũ là đầu vào tương thích, không phải bằng chứng kiểm thử runtime. Chưa chạy ứng dụng hoặc kiểm thử API trong công việc viết spec này. jCodeMunch không được cung cấp trong toolset của lượt viết spec, nên không thực hiện thêm dò mã bằng công cụ thay thế trái chính sách repo. Khi lập kế hoạch triển khai phải đối chiếu chữ ký DTO, migration hiện hữu và cấu hình STOMP qua jCodeMunch; không dùng việc đó để tự đổi quyết định sản phẩm ở đây.

Spec này thay thế các quy tắc của thiết kế cũ về quyền sinh viên xác nhận, đóng ticket, mở lại, Staff sửa mọi ticket, deadline và lịch sử. Các hành vi không được sửa trong spec giữ theo module hiện có.

## 3. Các hướng đã cân nhắc

| Hướng | Lợi ích | Đánh đổi | Quyết định |
|---|---|---|---|
| A. Mở rộng ticket hiện có, thêm hành động nghiệp vụ và bảng lịch sử/vòng xử lý | Tái sử dụng API, Kanban, comment, tệp, notification; thay đổi vừa đủ | Cần migration và siết quyền Staff so với API cũ | **Chọn** |
| B. Tách workflow mới bên cạnh ticket cũ | Cách ly thay đổi trạng thái | Hai nguồn trạng thái, hai luồng quyền và đồng bộ dữ liệu | Không chọn |
| C. Viết lại ticket bằng workflow engine/event sourcing đầy đủ | Mở rộng được nhiều quy trình | Tăng công cụ, chuyển dữ liệu và chi phí vận hành quá phạm vi | Không chọn |

Agent chọn A theo ủy quyền “tự quyết tất cả”, đã nêu lựa chọn trong cuộc trao đổi. Đây là một dự án nâng cấp module, cần spec đầy đủ nhưng không viết lại nền tảng.

## 4. Vai trò và phân quyền

Giả định sản phẩm cho đợt này: một BQL KTX quản lý tập trung. Cả Admin và Staff đọc được toàn bộ ticket thuộc hệ thống này để tiếp nhận và phối hợp. Không tuyên bố hỗ trợ nhiều đơn vị quản lý độc lập hoặc case chỉ một nhóm bí mật được xem.

| Hành động | Người gửi | Staff | Admin |
|---|---|---|---|
| Xem ticket, lịch sử công khai, tệp công khai | Ticket của mình | Tất cả | Tất cả |
| Xem/viết ghi chú nội bộ | Không | Xem tất cả; viết khi được phân công | Tất cả |
| Comment công khai | Ticket của mình, khi chưa kết thúc | Khi được phân công | Tất cả |
| Nhận ticket chưa có người | Không | Có, nhận cho chính mình | Có |
| Đặt/thay/xóa danh sách người phụ trách | Không | Không | Có |
| Bắt đầu xử lý, báo đã xử lý, từ chối | Không | Khi được phân công | Có |
| Đổi priority, điều chỉnh hạn | Không | Không | Có, bắt buộc lý do |
| Xác nhận hoàn tất, báo chưa xong | Chỉ người gửi | Không thay người gửi | Không thay người gửi |
| Đóng ngoại lệ | Không | Không | Có, từ `RESOLVED`, có lý do |
| Yêu cầu xét mở lại sau đóng | Chỉ người gửi | Không | Không thay người gửi |
| Xét mở lại | Không | Không | Có, bắt buộc lý do |

- Giữ quan hệ nhiều assignee; mọi assignee có quyền xử lý như nhau, chưa thêm primary assignee.
- `claim` chỉ thành công khi `OPEN` và danh sách assignee rỗng. Claim thêm chính người gọi, không tự đổi trạng thái, không tính là phản hồi đầu tiên. Hai Staff nhận đồng thời: chỉ một người thành công.
- Khi BQL bắt đầu/báo kết quả xử lý hoặc cập nhật phân công cho `IN_PROGRESS`/`RESOLVED`, phải có ít nhất một assignee đang có role BQL. Admin có thể phân công chính mình khi không phải reporter. Không cho chủ động xóa người hợp lệ cuối cùng ở hai trạng thái này; thay cả danh sách trong một transaction được phép.
- Staff chưa được phân công chỉ đọc và nhận ticket còn trống; không comment, từ chối hoặc đổi trạng thái. Quyền được kiểm tra tại service trên dữ liệu hiện tại, không chỉ ẩn nút frontend.
- Người có cả role BQL và là reporter dùng hành động người gửi để xác nhận. Không được claim, tự đưa mình vào assignee, bắt đầu/xác nhận xử lý, từ chối, rút kết quả, đóng ngoại lệ hoặc xét yêu cầu mở lại của chính mình. Admin vẫn được điều phối ticket của mình cho BQL khác và chỉnh priority/hạn có audit. Comment do reporter viết, kể cả có role BQL, không tính là phản hồi của BQL cho SLA. Nếu chỉ có một BQL trong hệ thống, ticket do người đó báo phải chờ bổ sung người xử lý độc lập; không tự bỏ kiểm tra quyền.
- Nếu assignee bị khóa tài khoản hoặc mất role giữa chừng, không kéo thay đổi sang module quản lý tài khoản: giữ trạng thái và lịch sử phân công, nhưng chỉ người còn quyền là assignee hiệu lực. Khi không còn ai hợp lệ, detail/list có `needsReassignment=true`, Admin được báo và nhận các nhắc SLA; SLA vẫn chạy. Chặn BQL bắt đầu/resolve/rút kết quả đến khi phân công lại. Không chặn reporter confirm/rework hoặc auto-close; rework vẫn tạo `IN_PROGRESS` với cờ cần phân công lại, đây là ngoại lệ có chủ ý để không làm mất quyền phản hồi. Admin xét mở lại phải phân công hợp lệ trong cùng transaction. Không gửi nội dung mới cho assignee đã mất quyền.
- Endpoint người gửi trả `404` cho ticket không tồn tại hoặc không thuộc người gọi. Hành động BQL sai quyền trả `403`; có quyền nhưng trạng thái không còn hợp lệ trả `409`.
- Tệp của comment nội bộ luôn chỉ BQL được đọc, kể cả reporter biết `storedName`. Áp dụng cùng chính sách cho download, thumbnail, history và notification.
- Không đổi tên/role hoặc mở rộng quyền của Staff ở module khác.

## 5. Trạng thái và quy trình

Giữ enum `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `REJECTED`. Trên UI, `RESOLVED` được gọi là **“Đã xử lý — chờ bạn xác nhận”**, `CLOSED` là **“Đã đóng”** và hiển thị cách đóng. Không thêm trạng thái `REOPENED`; mở lại tạo vòng xử lý mới và trở về `IN_PROGRESS`.

| Từ | Hành động | Đến | Điều kiện và hiệu ứng |
|---|---|---|---|
| `OPEN` | Bắt đầu xử lý | `IN_PROGRESS` | BQL có quyền; có assignee; nội dung tiếp nhận công khai bắt buộc |
| `OPEN`, `IN_PROGRESS` | Từ chối | `REJECTED` | BQL có quyền; lý do công khai bắt buộc; kết thúc SLA xử lý với kết quả loại trừ |
| `IN_PROGRESS` | Báo đã xử lý | `RESOLVED` | Nội dung xử lý bắt buộc; kết thúc vòng hiện tại; bắt đầu chờ xác nhận 72 giờ |
| `RESOLVED` | Sinh viên xác nhận | `CLOSED` | Đúng reporter; `closeReason=STUDENT_CONFIRMED` |
| `RESOLVED` | Sinh viên báo chưa xong | `IN_PROGRESS` | Lý do bắt buộc; tạo vòng xử lý mới; giữ assignee |
| `RESOLVED` | BQL rút kết quả xử lý | `IN_PROGRESS` | BQL có quyền; lý do công khai; tạo vòng mới |
| `RESOLVED` | Hết 72 giờ | `CLOSED` | Scheduler kiểm tra lại; `closeReason=AUTO_CLOSED` |
| `RESOLVED` | Admin đóng ngoại lệ | `CLOSED` | Lý do công khai; `closeReason=ADMIN_CLOSED` |
| `CLOSED` | Admin duyệt yêu cầu mở lại | `IN_PROGRESS` | Yêu cầu hợp lệ đang chờ; có assignee; tạo vòng mới |

Các chuyển khác bị từ chối, gồm `IN_PROGRESS → OPEN`, `OPEN → RESOLVED`, `IN_PROGRESS → CLOSED`, `CLOSED → IN_PROGRESS` trực tiếp và mọi chuyển từ `REJECTED`. Loại bỏ đường lùi về `OPEN` để tránh bỏ trách nhiệm; Admin đổi assignee để điều phối lại.

### Báo đã xử lý và trao đổi

- Nội dung tiếp nhận, xử lý, từ chối, yêu cầu tiếp tục, xét mở lại và đóng ngoại lệ: sau trim phải có 10–4.000 ký tự. Không tự điền note mẫu thay cho người xử lý.
- Nội dung xử lý được lưu thành comment công khai và liên kết với sự kiện/vòng xử lý. Có thể đính kèm minh chứng, không bắt buộc ảnh trong phòng.
- Comment thông thường vẫn dùng validation hiện có. Comment “vẫn hỏng” không tự đổi trạng thái; UI có hành động “Vấn đề chưa được giải quyết” riêng và hướng dẫn rõ.
- Người gửi không comment thông thường khi `CLOSED` hoặc `REJECTED`; dùng yêu cầu xét mở lại khi đủ điều kiện. Action reopen tạo note công khai chuyên biệt gắn request kể cả khi ticket đang đóng, không mở quyền comment thông thường. BQL chỉ được thêm ghi chú nội bộ sau kết thúc, ngoài note quyết định xét mở lại do action tạo, tránh tạo cuộc trao đổi không có vòng xử lý chịu trách nhiệm.
- Lý do từ chối phải nêu cách liên hệ/giải quyết tiếp phù hợp. `REJECTED` không nằm trong cơ chế mở lại; sinh viên có thể tạo ticket mới. Chưa thêm liên kết ticket thay thế.

### Tự đóng

- `confirmationDueAt = resolvedAt + 72 giờ`; gửi một nhắc ở `resolvedAt + 48 giờ`, không nhắc lặp.
- Chỉ tự đóng khi còn đúng trạng thái `RESOLVED` và đúng vòng xử lý đã lên lịch. Đồng hồ chạy liên tục kể cả cuối tuần.
- Nếu scheduler chạy muộn, đóng tại thời điểm thực chạy và lưu `closedAt` thật; ghi thêm hạn dự kiến trong event. Không backdate.
- Khi báo chưa xong, hủy hiệu lực nhắc/đóng của vòng cũ; lần resolve mới tạo lại mốc 48/72 giờ.
- Nội dung thông báo từ lúc resolve nêu rõ thời hạn và việc im lặng sẽ tự đóng. Tự đóng không được diễn giải là sinh viên hài lòng.
- Nếu kênh email/WebSocket lỗi, trạng thái vẫn theo mốc đã công bố; outbox giữ việc gửi lại. Lỗi gửi phải nhìn thấy được, không dùng làm bằng chứng sinh viên đã nhận/đọc.

### Xét mở lại sau đóng

- Mỗi lần đóng mở một cửa sổ `closedAt + 168 giờ`; nhận yêu cầu tại thời điểm nhỏ hơn hoặc bằng mốc này, không cho nộp quá hạn.
- Reporter gửi lý do và có thể có tệp. Ticket vẫn `CLOSED` trong lúc chờ xét; yêu cầu có trạng thái `PENDING`, `APPROVED`, `REJECTED` riêng.
- Mỗi lần đóng nhận tối đa một yêu cầu, không sửa/rút/nộp lại sau bị từ chối. Cần trao đổi thêm thì BQL xử lý ngoài luồng hoặc sinh viên tạo ticket mới. Lần đóng tiếp theo sau mở lại có cửa sổ mới.
- Admin được xét một yêu cầu đã nộp đúng hạn kể cả lúc xét đã qua ngày thứ 7. Mục tiêu hiển thị xử lý trong 24 giờ, chưa thêm một SLA/escalation riêng.
- Duyệt: quyết định, lý do, assignee và vòng mới ghi cùng transaction. Nếu người cũ không còn hợp lệ, Admin phải cấp danh sách assignee hợp lệ.
- Từ chối: ticket giữ `CLOSED`, ghi người xét, thời điểm, lý do công khai và thông báo reporter. Không sửa lại lý do đóng trước.
- Sau mở lại, `closedAt` và `closeReason` ở snapshot hiện tại trở về null; các lần đóng trước còn nguyên trong history.

## 6. SLA và hạn xử lý

### Chính sách mặc định

Các giá trị dưới đây do agent chọn cho MVP, là mục tiêu vận hành cấu hình được, chưa phải cam kết thực tế của KTX. Tính **24/7 theo giờ trôi qua**, dùng `Asia/Ho_Chi_Minh` khi hiển thị.

| Nhóm chính sách | Priority lưu trong API/DB | Phản hồi đầu tiên | Xử lý mỗi vòng |
|---|---|---|---|
| Thấp | `LOW` | 24 giờ | 120 giờ |
| Thông thường | `MEDIUM` | 8 giờ | 72 giờ |
| Cao/khẩn | `HIGH`, `URGENT` | 1 giờ | 24 giờ |

Giữ cả 4 priority hiện có để không phá dữ liệu/client; map `HIGH` và `URGENT` vào cùng nhóm SLA để triển khai 3 chính sách như đề xuất. `URGENT` vẫn đứng trước `HIGH` trong hàng đợi. Tạo ticket mặc định `MEDIUM`; Admin đổi priority có lý do. Chính sách là cấu hình phiên bản hóa, chưa làm CRUD cấu hình trên UI.

### Mốc bắt đầu và kết thúc

- SLA phản hồi đầu tiên bắt đầu tại `createdAt`, chỉ có một lần cho toàn ticket.
- Nghĩa vụ phản hồi chỉ còn mở khi `OPEN/IN_PROGRESS` và chưa có `firstResponseAt`. Khi có phản hồi hợp lệ, ghi `responseOutcome=RESPONDED`, `responseEndedAt=firstResponseAt`. Khi ticket ra `RESOLVED/CLOSED/REJECTED`, không bao giờ tiếp tục nhắc phản hồi: dữ liệu mới phải có firstResponse từ note BQL khác reporter; legacy thiếu bằng chứng ghi `LEGACY_UNKNOWN` và loại khỏi tỷ lệ phản hồi, không ghi thời gian giả. Khi có dữ liệu lỗi bất thường, ghi `EXCLUDED_AT_END` cùng lỗi quan sát được để sửa; vi phạm đã biết vẫn giữ. Mở lại không khởi động lại nghĩa vụ phản hồi đã kết thúc.
- Phản hồi hợp lệ: comment công khai có nội dung từ BQL khác reporter; hành động bắt đầu xử lý với nội dung công khai; hoặc note giải quyết/từ chối công khai nếu chưa có phản hồi trước. Claim, phân công, đổi priority/hạn, ghi chú nội bộ và thông báo tự động không được tính.
- Vòng 1 có `openedAt=createdAt`; các vòng sau có `openedAt` bằng thời điểm trở về `IN_PROGRESS`. Mỗi vòng có SLA xử lý riêng, không khởi động lại SLA phản hồi đầu tiên.
- SLA xử lý dừng khi báo `RESOLVED`. Thời gian chờ sinh viên, chờ xét mở lại và thời gian đã đóng không nằm trong vòng xử lý; báo cáo tổng thời gian đầu-cuối vẫn có thể bao gồm các khoảng này và phải ghi rõ.
- `REJECTED` dừng đồng hồ; loại khỏi tỷ lệ xử lý thành công nhưng hiển thị số từ chối và độ trễ phản hồi. Vi phạm đã xảy ra trước khi từ chối vẫn lưu, không xóa khỏi tỷ lệ vi phạm.
- Không tạm dừng SLA khi chờ vật tư, sinh viên hoặc bên ngoài. Dùng note và điều chỉnh hạn có lý do; chưa thêm trạng thái `ON_HOLD`.
- Hoàn thành đúng hạn khi thời điểm hoàn thành `<= dueAt`; đang chưa hoàn thành thì quá hạn khi `now > dueAt`.

### Hạn chuẩn, hạn điều hành và thay đổi

- Lưu `baselineResponseDueAt` cho ticket và `baselineResolutionDueAt` cho từng vòng theo policy/priority snapshot lúc mở vòng. Các hạn chuẩn này bất biến.
- Lưu riêng `responseDueAt` và `resolutionDueAt` là hạn điều hành hiện hành. Admin được đổi nếu còn nghĩa vụ chưa hoàn thành; lý do bắt buộc, không đặt null hoặc trước mốc bắt đầu. Có thể đặt hạn đã ở quá khứ nhưng phải hiển thị quá hạn ngay.
- Đổi priority tính lại hạn điều hành của nghĩa vụ đang mở từ mốc bắt đầu gốc, không từ thời điểm chỉnh. Không tính lại nghĩa vụ đã xong hoặc vòng cũ. Vòng mới dùng priority/policy đang có tại lúc mở vòng.
- Đổi priority/hạn không sửa baseline, không sửa số liệu đã hoàn thành, không xóa `breachedAt` đã ghi. Trước mỗi lần thay hạn phải đánh giá vi phạm của hạn cũ tại thời điểm thay đổi, tránh scheduler chưa kịp chạy làm mất vi phạm.
- Báo cáo SLA chuẩn so với baseline; màn điều hành hiển thị hạn hiện hành và cờ “đã từng quá hạn điều hành” riêng. Tỷ lệ SLA không lấy deadline đã nới làm mẫu số mới.
- Policy mới chỉ áp dụng ticket/vòng tạo sau thời điểm có hiệu lực; snapshot cũ không được tính lại.

### Nhắc và danh sách cần hành động

- Mở rộng scheduler hiện có, chu kỳ mỗi 5 phút. Không xây sender hay topic mới cho từng loại nhắc.
- Khi quá hạn phản hồi hoặc xử lý: WebSocket đến assignee, nếu chưa có assignee thì đến Admin; loại trùng người nhận. Admin thấy tất cả vi phạm qua bộ lọc.
- Nhắc lặp tối đa mỗi 24 giờ cho từng nghĩa vụ còn mở; email gom bản tin hằng ngày lúc 08:00 giờ Việt Nam cho assignee, hoặc Admin nếu chưa có người. Không phát email riêng mỗi 5 phút.
- Đổi hạn không xóa lịch nhắc đã gửi. Hạn điều hành mới chỉ phát nhắc khi thực sự đến hạn, không gửi lại ngay chỉ vì vừa chỉnh.
- Không tiếp tục nhắc xử lý khi `RESOLVED`, `CLOSED`, `REJECTED`. Nhắc xác nhận kết quả là loại thông báo khác.
- `RESOLVED` nằm ở hàng chờ xác nhận; không tính vào backlog cần sửa. `CLOSED` có yêu cầu mở lại nằm ở hàng chờ Admin xét.

## 7. Dữ liệu và tính toàn vẹn

Tên dưới đây là tên logic; builder có thể điều chỉnh tên Java/SQL theo convention hiện có, không đổi ý nghĩa. Giữ SQL Server, Flyway, cấu trúc service/mapper/security hiện tại.

| Thành phần | Dữ liệu cần lưu |
|---|---|
| Bổ sung `Ticket` | `version`, `responseStartedAt`, `firstResponseAt`, `responseOutcome`, `responseEndedAt`, `firstResolvedAt`, `latestResolvedAt`, `responseDueAt`, `baselineResponseDueAt`, response-policy snapshot, response breach/reminder timestamps, `currentCycleId`, `confirmationDueAt`, `confirmationRemindedAt`, `closeReason`, `phase3EnabledAt` |
| `TicketProcessingCycle` | `id`, `ticketId`, `cycleNo`, `openedAt`, `openedBy`, `openReason`, `resolvedAt`, `resolutionCommentId`, `endedAt`, `endOutcome`, `legacyImported`, policy/priority snapshot, `baselineResolutionDueAt`, `resolutionDueAt`, breach/reminder timestamps, `version` |
| `TicketHistoryEvent` | `id`, `ticketId`, `cycleId`, `sequence`, `type`, `actorId` hoặc system actor, `occurredAt`, `visibility`, `fromStatus`, `toStatus`, lý do/nội dung, dữ liệu trước/sau dạng payload có schema version, command/event ID |
| `TicketReopenRequest` | `id`, `ticketId`, `closureEventId`, `requesterId`, `reason`, `requestedAt`, `status`, `reviewerId`, `reviewedAt`, `decisionReason`, `requestCommentId` |
| `TicketCommandReceipt` | actor, ticket/resource, idempotency key, fingerprint request, kết quả tham chiếu, `createdAt`, `expiresAt` |
| Outbox/delivery ticket | event ID, ticket ID, loại, recipient, channel, trạng thái/retry, `nextAttemptAt`, `sentAt`, lỗi gần nhất, dedup key |

- Một vòng được mở cho mỗi lần bắt đầu chu kỳ; `cycleNo` tăng, unique `(ticketId, cycleNo)`. Không tạo vòng mới khi `OPEN → IN_PROGRESS` lần đầu.
- Chỉ một vòng chưa kết thúc của mỗi ticket; `RESOLVED`/`REJECTED` phải kết thúc vòng bằng `endOutcome=RESOLVED/REJECTED`, lưu `endedAt`. Với `REJECTED`, `resolvedAt` để null; không giả làm đã sửa xong.
- Unique `closureEventId` trong reopen request bảo đảm một yêu cầu mỗi lần đóng. Unique `(ticketId, sequence)` cho history.
- History và các note làm bằng chứng được ghi nối tiếp, không sửa/xóa. Có thể thêm sự kiện đính chính; không xóa dữ kiện cũ.
- `firstResolvedAt` là lần resolve đầu, bất biến. `latestResolvedAt` là lần resolve gần nhất và không bị xóa khi mở lại; trạng thái/vòng hiện tại cho biết còn đang xử lý.
- `resolvedAt` hiện có giữ nghĩa snapshot tương thích: có giá trị khi `RESOLVED`/`CLOSED`, có thể null khi mở lại. KPI tuyệt đối không đọc riêng field này thay cho cycle/history.
- `closeReason` nhận `STUDENT_CONFIRMED`, `AUTO_CLOSED`, `ADMIN_CLOSED`, `LEGACY_UNKNOWN`. Chỉ dùng `LEGACY_UNKNOWN` cho dữ liệu chuyển đổi.
- Dùng timestamp UTC cho dữ liệu mới; API mới trả ISO-8601 có `Z` hoặc offset, UI đổi sang giờ địa phương. Không tự đổi timezone các field cũ trên toàn hệ thống.
- Tệp minh chứng resolve/rework/reopen gắn comment công khai tương ứng, tái sử dụng attachment storage. Giới hạn giữ 5 tệp/lần, 10 MB/tệp, loại hiện có. Không làm storage thứ hai.
- Tệp đã được liên kết làm minh chứng resolve/rework/reopen không được hard delete qua API hiện có; trả `409 TICKET_EVIDENCE_LOCKED`. Các attachment khác giữ quy tắc xóa cũ nhưng phải qua kiểm tra visibility; việc xử lý dữ liệu theo yêu cầu xóa riêng tư ngoài phạm vi UI này.
- Index phục vụ danh sách status/assignee, pending reopen, response/resolution deadline, history theo ticket-sequence và outbox pending-nextAttempt; hình thức index phù hợp SQL Server do builder quyết định.

## 8. API và hợp đồng frontend

Giữ `ApiResponse<T>`, UUID và prefix hiện có. Request mới không nhận reporter/actor từ client; lấy từ principal. Các trường thời gian trong response mới dùng định dạng nêu trên.

### Hành động mới

| Method/path | Người gọi | Dữ liệu chính |
|---|---|---|
| `POST /api/tickets/{id}/claim` | Staff/Admin | `expectedVersion` |
| `POST /api/tickets/{id}/resolve` | BQL có quyền | Multipart `data={expectedVersion, resolutionNote}`, `files` tùy chọn |
| `POST /api/users/me/tickets/{id}/confirm` | Reporter | `expectedVersion` |
| `POST /api/users/me/tickets/{id}/request-rework` | Reporter | Multipart `data={expectedVersion, reason}`, `files` tùy chọn |
| `POST /api/users/me/tickets/{id}/reopen-requests` | Reporter | Multipart `data={expectedVersion, reason}`, `files` tùy chọn |
| `GET /api/users/me/tickets/{id}/reopen-requests` | Reporter | Danh sách yêu cầu và kết quả xét công khai |
| `GET /api/tickets/{id}/reopen-requests` | BQL | Danh sách cùng dữ liệu xét |
| `POST /api/tickets/{id}/reopen-requests/{requestId}/review` | Admin | `expectedVersion`, `decision=APPROVE/REJECT`, `reason`, `assigneeIds` nếu cần |
| `PATCH /api/tickets/{id}/sla-deadlines` | Admin | `expectedVersion`, hạn điều hành response/resolution cần đổi, `reason` |
| `GET /api/users/me/tickets/{id}/history` | Reporter | Phân trang, chỉ sự kiện công khai |
| `GET /api/tickets/{id}/history` | BQL | Phân trang, gồm sự kiện nội bộ |
| `GET /api/tickets/metrics` | BQL | Khoảng thời gian và bộ lọc, định nghĩa ở mục 11 |
| `GET /api/tickets/metrics/records` | BQL | `metric`, cùng khoảng thời gian/bộ lọc của aggregate; phân trang các bản ghi và lý do được tính/loại |

Các action đổi dữ liệu trả detail mới, riêng nộp/xét reopen trả detail và request đã cập nhật. Action JSON/resolve/rework/reopen/review yêu cầu `Idempotency-Key`. HTTP thành công giữ `200` theo module hiện tại.

### Điều chỉnh API hiện có

- `PATCH .../status`: thêm `expectedVersion`, `reason`; chỉ thực hiện những chuyển hợp lệ ở mục 5. `RESOLVED` qua route này vẫn nhận `resolutionNote` và gọi cùng logic với `/resolve`, nhưng không có file. `RESOLVED → CLOSED` chỉ Admin và ghi `ADMIN_CLOSED`; không thể giả làm sinh viên xác nhận. `RESOLVED → IN_PROGRESS` từ BQL phải có lý do. Cấm route này mở trực tiếp từ `CLOSED`.
- `PUT .../assignees` và `PATCH .../priority`: chỉ Admin; thêm version và reason; ghi history. Danh sách được thay nguyên bộ như cũ.
- `PATCH .../due-date`: giữ route chuyển tiếp; chỉ Admin; thêm version và reason. `dueDate` không-null được hiểu đến cuối ngày địa phương (`23:59:59.9999999`), đổi thành hạn xử lý điều hành của vòng hiện tại. Không ảnh hưởng hạn phản hồi, không thay baseline. Không cho xóa hạn. Nếu gửi cùng ngày đang có nhưng với key mới, vẫn là yêu cầu điều chỉnh hợp lệ có history; không tự xóa breach/reminder.
- Các mutation ticket cũ nêu trên cũng phải gửi `Idempotency-Key`. Comment/create/upload/delete thông thường giữ contract hiện tại; không hứa exactly-once cho các request cũ đó.
- Summary/detail bổ sung `version`, `allowedActions`, các mốc SLA, baseline breach/effective breach, `confirmationDueAt`, `closeReason`, `reopenEligibleUntil`, `pendingReopenRequest`, `cycleNo`, `phase3EnabledAt`, `needsReassignment`. SLA deadline resolution lấy từ current cycle; chưa có dữ liệu thì field vắng/null theo envelope hiện tại.
- `allowedActions` do backend tính theo người gọi, trạng thái, assignee và thời gian; không trả các action nội bộ cho reporter. Backend vẫn kiểm tra khi thực thi.
- `GET /api/tickets`: giữ filter cũ; thêm `slaType=RESPONSE/RESOLUTION`, `needsAction=UNASSIGNED/AWAITING_CONFIRMATION/REOPEN_REVIEW`, `buildingNodeId` gồm cây con, `createdFrom/createdTo`.
- Với ticket Phase 3, `overdue=true` nghĩa vụ SLA hiện hành đang mở và quá hạn; `slaType` là filter bổ sung. Ticket legacy chưa kích hoạt giữ nghĩa overdue theo `dueDate` cũ. Trả `overdueSource=SLA/LEGACY_DUE_DATE` để frontend phân biệt, không trộn thành một KPI.
- `GET .../board` giữ đủ 5 key và shape hiện có, áp dụng scope quyền. Mỗi thẻ bổ sung dữ liệu tóm tắt mới; chưa đổi API board thành phân trang.
- Không thay contract `GET /api/users/me/tickets` từ array thành Page trong đợt này. History/reopen list mới dùng phân trang với `page=0`, `size=20`, tối đa 100.

### Lỗi bổ sung

Giữ mã `TKT-001…009` cũ; thêm mã có tên rõ cho command mới, mapping qua cơ chế exception chung:

| HTTP | Mã logic | Khi nào |
|---|---|---|
| 400 | `TICKET_REASON_REQUIRED`, `TICKET_VERSION_REQUIRED`, `TICKET_IDEMPOTENCY_KEY_REQUIRED` | Thiếu hoặc không hợp lệ dữ liệu bắt buộc |
| 403 | `TICKET_ACTION_FORBIDDEN` | Role/phân công không cho phép |
| 404 | `TICKET_NOT_FOUND` | Không có ticket hoặc người gửi không có quyền biết |
| 409 | `TICKET_VERSION_CONFLICT`, `TICKET_ACTION_CONFLICT` | Version cũ hoặc trạng thái/assignee đã đổi |
| 409 | `TICKET_REOPEN_WINDOW_EXPIRED`, `TICKET_REOPEN_ALREADY_REQUESTED` | Hết cửa sổ hoặc đã nộp cho lần đóng này |
| 409 | `TICKET_IDEMPOTENCY_CONFLICT`, `TICKET_EVIDENCE_LOCKED` | Key dùng lại với payload khác hoặc xóa bằng chứng đã khóa |

Frontend gặp `409` phải tải lại detail và hiển thị trạng thái mới, không tự lặp vô hạn action cũ. Security-layer `401/403` giữ theo cơ chế hệ thống hiện có, không yêu cầu viết lại toàn bộ error envelope.

## 9. Đồng thời, transaction và thử lại

- Mỗi chuyển trạng thái cập nhật ticket, vòng xử lý, request mở lại nếu có, history, receipt và outbox trong cùng transaction DB. Tất cả dùng cùng một thời điểm `now` do server chốt.
- Mọi command có `expectedVersion`; optimistic locking bảo vệ snapshot. Claim dùng thêm điều kiện assignee rỗng trong cùng khóa/transaction. Scheduler lấy lại ticket và kiểm tra version, vòng và hạn trước khi ghi.
- Với một key đã thành công, kiểm tra quyền hiện tại rồi trả lại kết quả tham chiếu với `replayed=true` và detail hiện tại, không thực thi lại hoặc tăng version. Kiểm tra receipt trước expectedVersion để retry hợp lệ không bị lỗi vì chính lần đầu đã tăng version.
- Scope unique của key: actor + operation + ticket/resource + key. Lưu fingerprint gồm payload, expectedVersion và digest file; cùng key khác nội dung trả `409`. Receipt giữ tối thiểu 30 ngày. Khi key hết hạn, quy tắc trạng thái/unique closure/version vẫn ngăn tạo thêm vòng hoặc yêu cầu trùng cho một snapshot cũ.
- Xác nhận và rework/auto-close chạy đồng thời: chỉ một command commit. Lệnh còn lại reload rồi trả conflict hoặc bỏ qua nếu scheduler; không có hai kết quả đóng/mở cho cùng version.
- Attachment ghi đĩa không transactional: stage trước, chỉ liên kết trong transaction; rollback xóa file mới khi có thể, có tác vụ dọn file stage mồ côi. Không gửi thông báo trước commit.
- Scheduler chạy nhiều instance vẫn không tạo lặp close/reminder/breach: cạnh tranh version + unique event/dedup key, không dựa vào một tiến trình luôn duy nhất.

## 10. Thông báo và riêng tư

Tái sử dụng `NotificationProducer`, Kafka và sender hiện có. Kênh đợt này: WebSocket và email khi đã cấu hình; chưa thêm push/SMS. Đây là khả năng cần kiểm chứng khi triển khai, không khẳng định email đang hoạt động.

| Sự kiện | Người nhận | Kênh |
|---|---|---|
| Ticket mới chưa có người | Admin | WebSocket + email theo luồng hiện có |
| Nhận/phân công | Reporter và assignee mới | WebSocket + email |
| Comment công khai | Bên còn lại; phía BQL là assignee, fallback Admin nếu chưa có ai | WebSocket + email |
| Note nội bộ | Assignee/Admin, trừ tác giả | WebSocket, không gửi reporter |
| Bắt đầu, resolve, reject, đóng | Reporter | WebSocket + email |
| Nhắc xác nhận 48 giờ | Reporter | WebSocket + email |
| Rework | Assignee; fallback Admin | WebSocket + email |
| Nộp yêu cầu mở lại | Admin | WebSocket + email |
| Duyệt/từ chối mở lại | Reporter; duyệt thêm assignee | WebSocket + email |
| Quá SLA | Theo mục 6 | WebSocket + email digest |

- Chỉ tạo một business event cho một hành động; không vừa gửi “comment mới” vừa “đã xử lý” cho cùng note resolve. Loại trùng người nhận và loại tác giả khi không cần thông báo cho chính mình.
- Lưu outbox ticket để một lần thay trạng thái không mất sự kiện nếu tiến trình chết sau commit. Dispatcher có thể phát Kafka ít nhất một lần; delivery dedup theo `(businessEventId, recipientId, channel, notificationKind)`.
- Không xây lại notification platform toàn hệ thống: thêm outbox/dedup tối thiểu cho event ticket mới và các action ticket được nâng cấp. Các event module khác giữ nguyên.
- Trạng thái gửi: `PENDING`, `SENT`, `FAILED` với lần thử/lỗi; tối đa 5 lần theo backoff 1, 5, 15, 60, 240 phút rồi chuyển chờ xử lý. Không nhận là đã đọc. WebSocket không phải inbox bền vững: API/history vẫn là nguồn trạng thái.
- Dedup chặn gửi lặp có thể xác định trước; email vẫn có thể trùng khi SMTP đã nhận nhưng worker chết trước ghi `SENT`. Không cam kết exactly-once với SMTP; acceptance kiểm tra retry bình thường và nêu rõ cửa sổ lỗi này.
- Dùng đích WebSocket theo người dùng đã xác thực. Không phát nội dung ticket/ghi chú nội bộ lên topic chung mà client tùy ý subscribe; nếu giữ topic theo ticket, phải kiểm tra quyền subscribe và không dùng topic đó cho nội dung nội bộ. Ưu tiên chỉ phát invalidation chứa ticket ID đến người có quyền rồi frontend GET lại.
- Email chỉ nêu mã ticket, trạng thái và đường dẫn đăng nhập; không nhúng mô tả riêng tư, tệp, email cư dân khác hoặc note nội bộ.

## 11. Dữ liệu đo và nghiệm thu số liệu

Đợt này làm endpoint metrics ticket và dữ liệu danh sách để đối chiếu; chưa xây dashboard incident hay thay toàn bộ analytics cũ.

- Mọi phép đo chỉ gồm ticket có dữ liệu đầy đủ từ `phase3EnabledAt=createdAt`, hoặc cycle sau kích hoạt với nhãn cohort riêng. Không trộn seed/demo hoặc dữ liệu legacy thiếu lịch sử vào kết quả thật.
- Chọn cohort ticket theo `createdAt ∈ [from,to)` UTC. Filter category, priority hiện tại, khu vực hiện tại và assignee hiện tại có ý nghĩa “theo snapshot hiện tại”, không giả là nhân sự tại thời điểm lịch sử. Trả `asOf`, timezone và filter áp dụng.
- Thời gian phản hồi đầu tiên: median `firstResponseAt-createdAt`, số có phản hồi và số chưa phản hồi; loại phản hồi tự động/nội bộ/theo chính reporter.
- Thời gian giải quyết đầu: median `firstResolvedAt-createdAt` trên ticket từng resolve; trả số mẫu. Thời gian đến giải quyết gần nhất: `latestResolvedAt-createdAt` cho ticket đang `RESOLVED/CLOSED`, có bao gồm khoảng chờ/mở lại và được gắn nhãn rõ.
- SLA phản hồi chuẩn: mẫu số ticket trong cohort có baseline response deadline `<= asOf`; vi phạm khi phản hồi sau baseline hoặc chưa phản hồi tại `asOf > baseline`. Ticket bị từ chối trước hạn chưa phản hồi thì loại với lý do, không tính đạt giả; từ chối với note công khai đủ điều kiện đã là phản hồi.
- SLA xử lý chuẩn tính theo vòng: chọn cycle có `openedAt ∈ [from,to)` và baseline deadline đã tới. Resolve trước/đúng baseline là đạt; chưa resolve và quá baseline hoặc resolve muộn là vi phạm. Vòng bị từ chối trước/đúng baseline được loại khỏi mẫu số xử lý, báo số loại riêng; từ chối sau baseline vẫn là vi phạm. Nếu mẫu số 0 trả null, không chia 0 hoặc trả 100%.
- Tỷ lệ vi phạm ticket là phép OR các nghĩa vụ đủ điều kiện của ticket; không cộng response và resolution rồi gọi đó là số ticket. Endpoint trả cả `ticketCount` và `obligationCount` có nhãn.
- Tỷ lệ xử lý lại trước đóng: số ticket từng quay `RESOLVED → IN_PROGRESS` chia số ticket có lần resolve đầu đã đủ 72 giờ theo dõi; chỉ dùng tử số thuộc cùng mẫu số. Báo thêm số sự kiện rework và số ticket riêng.
- Yêu cầu mở lại sau đóng: báo số yêu cầu, số chờ/duyệt/từ chối và số ticket; tỷ lệ có yêu cầu tính trên các lần đóng có đủ 7 ngày theo dõi, tử số cũng chỉ thuộc những lần đóng đó. Tách với rework trước đóng, không trộn cửa sổ 72 giờ và 7 ngày.
- Cách kết thúc: số closure event trong khoảng theo `STUDENT_CONFIRMED`, `AUTO_CLOSED`, `ADMIN_CLOSED`; không gọi auto-close là hài lòng. Một ticket có thể có nhiều closure event, nên trả thêm số ticket khác nhau.
- Backlog tại `asOf=now`: `OPEN/IN_PROGRESS`, nhóm chưa phân công, priority, tuổi từ tạo và tuổi vòng hiện tại; trả cả `RESOLVED` chờ xác nhận, `CLOSED` chờ xét như hàng chờ riêng.
- Metrics chỉ hỗ trợ `asOf=now` trong đợt này; không nhận thời điểm quá khứ cho snapshot backlog. Event-time timestamps phải không lớn hơn `asOf`. Mỗi aggregate có count và endpoint/list filter đủ để truy ra bản ghi tạo ra nó; median dùng trung bình hai số giữa khi mẫu chẵn.
- Endpoint `/metrics/records` trả ticket ID, cycle/closure ID nếu metric theo vòng/lần đóng, các mốc gốc, deadline chuẩn, giá trị tính, `included` và lý do loại. Dùng cùng predicate với aggregate; cần đủ để đối chiếu cả tử số và mẫu số, không chỉ link về danh sách ticket tổng quát. Phân trang mặc định 20, tối đa 100.
- Không có dữ liệu: count=0, thời lượng/tỷ lệ=null; frontend hiển thị “Chưa đủ dữ liệu”. Dữ liệu legacy có nhãn và excluded count, không tự gán mốc để làm đẹp số.

## 12. Frontend cần đáp ứng

Đây là yêu cầu hành vi và dữ liệu, chưa phải thiết kế bố cục/giao diện chi tiết.

**Sinh viên:** danh sách yêu cầu; detail có người phụ trách, trạng thái, nội dung xử lý, hạn hiện hành và timeline công khai. Khi `RESOLVED`, có “Xác nhận đã xong” và “Vấn đề chưa được giải quyết”, mốc tự đóng rõ ràng. Khi đóng, hiển thị cách đóng, hạn nộp yêu cầu mở lại và kết quả xét. Không cho comment thường để thay thế action rework/reopen.

**Staff:** danh sách toàn bộ, nhóm chưa có người, việc của tôi, quá SLA; nhận việc chưa có người; xử lý và báo kết quả trong ticket được phân công. Ticket chỉ đọc không có nút sửa. Note nội bộ có nhãn riêng và không tự trở thành note công khai.

**Admin:** mọi thao tác Staff, phân công, priority/hạn có lý do, đóng ngoại lệ và hàng chờ xét mở lại. Màn xét hiển thị kết quả xử lý, lý do đóng trước và lý do sinh viên phản hồi. Không tự chọn “sinh viên đã xác nhận”.

Các nút dùng `allowedActions`; disable khi đang gửi, giữ cùng idempotency key khi retry cùng thao tác. Đóng/mở lại có phản hồi thành công sau API commit; không báo thành công chỉ vì WebSocket đã phát.

## 13. Migration và tương thích

- Thêm migration Flyway, không dựa vào `ddl-auto=update`; không sửa migration đã chạy hoặc tái tạo bảng ticket.
- Thêm bảng/field nullable trước, version mặc định 0; validate rồi mới đặt constraint phù hợp. Không đổi enum priority hoặc UUID/code ticket.
- Trước kích hoạt Phase 3, preflight phải liệt kê legacy `IN_PROGRESS/RESOLVED` không có assignee BQL hiệu lực hoặc chỉ phân công chính reporter. Admin sửa phân công qua luồng hiện có trước khi cutover; không tự gán một Admin ngẫu nhiên, không đổi về OPEN/đóng để né. Nếu còn trường hợp lỗi, chặn bước kích hoạt/backfill nghiệp vụ và báo danh sách cần sửa; schema additive có thể đã triển khai an toàn. Như vậy không nhập dữ liệu lỗi vào bất biến mới. Việc mất quyền sau cutover áp dụng ngoại lệ `needsReassignment` ở mục 4.
- Ticket mới sau cutover có đủ policy snapshot/cycle/history và `phase3EnabledAt=createdAt`.
- Ticket legacy `OPEN/IN_PROGRESS`: khi cutover tạo cycle đang mở với `openedAt=cutoverAt`, gắn `legacyImported=true`, SLA xử lý tính từ cutover. Không giả đây là ngày tạo thật và không tham gia KPI baseline ticket mới. SLA phản hồi nếu chưa có bằng chứng chỉ bắt đầu tại cutover trong cohort legacy riêng, không đo hồi tố từ createdAt. Dùng `responseStartedAt` riêng cho trường hợp này.
- Ticket legacy `RESOLVED`: giữ trạng thái và dữ liệu cũ, đánh dấu thiếu lịch sử; cho reporter xác nhận/rework. Không auto-close hàng loạt khi deploy: chỉ lên mốc 48/72 giờ từ thời điểm kích hoạt, gửi thông báo chuyển đổi sau commit; deadline xác nhận được ghi rõ. Giữ `resolvedAt` cũ nếu có, không sửa thành cutover.
- Với legacy `RESOLVED`, tạo một cycle snapshot import `cycleNo=1`, `legacyImported=true`, `endOutcome=LEGACY_RESOLVED`, liên kết `currentCycleId`. Cho phép riêng cycle import có `openedAt/endedAt/resolvedAt` và baseline null khi không có mốc đáng tin; lưu giá trị cũ có nguồn nếu có, không suy ra ngày tạo vòng. Cycle này không chạy SLA/không vào KPI; vẫn là anchor cho lịch xác nhận 48/72 giờ tính từ `phase3EnabledAt`. Note import không giả là BQL từng gửi comment. Rework tạo cycle số 2 đủ dữ liệu như bình thường.
- Ticket legacy `CLOSED/REJECTED`: không tự mở lại, không suy ra người xác nhận; lý do đóng không xác định dùng `LEGACY_UNKNOWN`. Với `CLOSED` có timestamp đóng đáng tin và còn trong 7 ngày, cho yêu cầu mở lại; thiếu timestamp thì không tự dựng cửa sổ, giữ chỉ đọc và liên hệ BQL/tạo mới.
- Mỗi legacy `CLOSED` tạo một `LEGACY_CLOSURE_IMPORTED` anchor event với `occurredAt=cutoverAt`, `sourceClosedAt` bằng timestamp cũ nếu đáng tin, `closeReason=LEGACY_UNKNOWN`, actor system và nhãn “nhập dữ liệu cũ”. Reopen tham chiếu ID anchor này; cửa sổ tính từ `sourceClosedAt`, không từ lúc import. Event import không được đếm là một lần đóng mới trong KPI. Không cần dựng cycle giả cho ticket đã đóng/từ chối; khi duyệt mở lại có thể tạo cycle đầu tiên có dữ liệu đầy đủ.
- Legacy đã `RESOLVED/CLOSED/REJECTED` không có bằng chứng phản hồi: `responseOutcome=LEGACY_UNKNOWN`, không tạo SLA phản hồi đang mở và không nhắc phản hồi. Legacy còn `OPEN/IN_PROGRESS` được đo nghĩa vụ từ cutover trong cohort riêng; khi kết thúc áp dụng quy tắc dừng ở mục 6.
- Cycle mới phát sinh khi rework/mở lại ticket legacy được đo SLA xử lý từ lúc mở vòng, có nhãn legacy ticket. Không đưa vào thời gian phản hồi/giải quyết đầu của ticket mới.
- Migration chuyển timestamp legacy phải dùng giả định timezone hiện hành đã kiểm chứng; nếu không có bằng chứng, giữ field cũ, lưu nhãn unknown và không tính duration từ chúng. Không đoán UTC rồi âm thầm lệch 7 giờ.
- Với API cũ, response bổ sung là additive; bắt buộc version/key, siết quyền Staff, cấm chuyển sai và note thật là thay đổi contract có chủ ý. Cập nhật frontend và backend cùng đợt; không tuyên bố tương thích ghi hoàn toàn với client cũ.
- Chuẩn bị migration/backup và bản phát hành frontend tương ứng trước cutover. Khi cần dừng chức năng mới, khóa mutation bằng cờ triển khai và giữ đọc; không chạy backend cũ ghi tự do lên dữ liệu đã có cycle/reopen. Không drop bảng lịch sử để rollback.

## 14. Tiêu chí nghiệm thu

| Mã | Kịch bản | Kết quả bắt buộc |
|---|---|---|
| TKT-A01 | Hai Staff cùng claim một ticket trống | Một người nhận; người còn lại nhận conflict; một assignment event |
| TKT-A02 | Staff chưa được phân công sửa ticket; người gửi đọc ticket người khác | Staff bị chặn; người gửi nhận 404; không đổi dữ liệu |
| TKT-A03 | Bắt đầu xử lý, resolve có note và tệp tùy chọn | Có assignee, timestamps, public history, SLA/cycle và thông báo đúng |
| TKT-A04 | Resolve thiếu/ghi note trắng; cố dùng route status để đi tắt | Bị từ chối; không tự thêm note; không có outbox thành công |
| TKT-A05 | Reporter xác nhận kết quả | Đóng với `STUDENT_CONFIRMED`; người khác không làm được |
| TKT-A06 | Reporter báo chưa xong | Về `IN_PROGRESS`, tăng cycle; kết quả cũ còn nguyên; assignee được báo |
| TKT-A07 | Chờ 48/72 giờ bằng clock kiểm soát | Một nhắc; một auto-close đúng vòng; lý do `AUTO_CLOSED` |
| TKT-A08 | Rework đồng thời auto-close; confirm đồng thời rework | Một kết quả commit; không vừa đóng vừa tạo vòng từ cùng version |
| TKT-A09 | Nộp đúng mốc 7 ngày, quá mốc, nộp lặp | Đúng mốc được nhận; quá mốc bị chặn; tối đa một request/lần đóng |
| TKT-A10 | Admin duyệt sau ngày thứ 7 một request nộp đúng hạn; Staff cố xét | Admin duyệt được với assignee; Staff bị chặn; lý do và vòng mới đầy đủ |
| TKT-A11 | Admin từ chối mở lại | Giữ đóng, reporter nhận lý do, không nộp lại cho cùng closure |
| TKT-A12 | Comment công khai/nội bộ, assignment và email tự động | Chỉ phản hồi nghiệp vụ của BQL khác reporter tính firstResponse |
| TKT-A13 | Đổi priority/hạn trước/sau breach, gồm scheduler chưa chạy | Baseline giữ nguyên; breach cũ còn; hạn điều hành tính đúng mốc gốc |
| TKT-A14 | Retry cùng key, khác payload, stale version và nhiều scheduler | Không lặp vòng/history/outbox; lỗi conflict có thể hiểu được |
| TKT-A15 | Reporter đoán URL tệp nội bộ hoặc subscribe topic khác | Không nhận được nội dung/tệp/sự kiện nội bộ; GET và STOMP cùng policy |
| TKT-A16 | Transaction rollback; dispatcher chết trước/sau phát Kafka | Không báo thay đổi chưa commit; sự kiện committed được retry; dedup đúng giới hạn đã công bố |
| TKT-A17 | Dữ liệu kiểm soát có reopen, reject, auto-close, legacy và chưa đủ mẫu | Count, median, mẫu số, cohort và closure reason đúng; không có số fallback |
| TKT-A18 | Cutover legacy đang resolve, thiếu timestamp, client cũ ghi | Không đóng hàng loạt; không dựng lịch sử giả; client cũ lỗi rõ thay vì bypass |
| TKT-A19 | Xóa tệp đã làm minh chứng; tệp nội bộ chưa khóa | Minh chứng bị chặn xóa; tệp khác vẫn tuân thủ quyền visibility và quy tắc cũ |
| TKT-A20 | Resolve lại sau một lần rework | `firstResolvedAt` giữ, `latestResolvedAt` tăng, mốc xác nhận mới đúng; vòng cũ không bị ghi đè |
| TKT-A21 | Legacy đang xử lý/resolve không có người hợp lệ; legacy đã đóng xin mở lại | Chặn kích hoạt đến khi phân công đúng; reopen dùng closure import có nhãn, không tạo ngày đóng giả |
| TKT-A22 | Assignee mất role giữa chừng | Có cờ cần phân công lại; Admin nhận nhắc; chặn resolve; reporter vẫn rework/confirm được |
| TKT-A23 | Reporter có role BQL cố tự xử lý; ticket kết thúc thiếu response legacy | Cấm tự xử lý/xét; không nhắc phản hồi sau kết thúc; số liệu thiếu có nhãn và excluded count |

Kiểm chứng khi triển khai gồm unit test quy tắc trạng thái/SLA bằng clock cố định; integration test với SQL Server cho transaction, unique/locking và migration; API test quyền và attachment; kịch bản frontend đầu-cuối cho xác nhận, rework, reopen. Không coi kiểm thử thuần mock là bằng chứng đủ cho cạnh tranh DB/Kafka.

## 15. Phần builder được tự quyết và bàn giao

Builder tự quyết tên lớp/DTO, cấu trúc package theo module sẵn có, cách lưu payload history, tối ưu query/index, bố cục frontend và cách stage file, miễn giữ contract, quyền, thời gian và các bất biến ở trên. Có thể tái sử dụng outbox/dedup nếu khám phá repo chứng minh đã có cơ chế tương đương; không tạo lớp song song chỉ vì tên trong spec khác.

Các quyết định sản phẩm đã được agent chọn: BQL tập trung; Staff đọc toàn bộ nhưng chỉ sửa việc được giao; Admin điều phối/xét mở lại; nhiều assignee ngang quyền; 3 nhóm SLA cho 4 priority cũ; tính 24/7; 48/72 giờ nhắc/đóng; cửa sổ 7 ngày; một request cho mỗi closure; không làm lịch hẹn. Không còn câu hỏi sản phẩm bắt buộc gửi lại người dùng để hoàn thành spec.

Builder check độc lập ngày 02/11/2026 đã rà lại khả năng lập kế hoạch từ tài liệu. Các điểm cần làm rõ về assignee legacy, closure/cycle import, BQL tự xử lý ticket của mình và thu hồi role đã được bổ sung; lần kiểm tra lại kết luận không còn blocker quan trọng. Đây là review thiết kế, không phải bằng chứng tính năng đã được cài đặt hay kiểm thử.

Sau khi spec được phê duyệt mới lập implementation plan bằng `superpowers:writing-plans`; chưa viết product code, cài dependency hoặc triển khai tính năng trong công việc bàn giao tài liệu này.
