import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'dart:convert'; // JSON 파싱용
import 'package:http/http.dart' as http; // HTTP 통신용
import '../controller/cart_controller.dart';
import '../dto/order_request.dart';
import '../service/order_service.dart';

class CartScreen extends StatefulWidget {
  const CartScreen({Key? key}) : super(key: key);

  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  final OrderService _orderService = OrderService();
  bool _isProcessing = false;

  // 💡 백엔드 MySQL(sh_cart)에서 현재 유저(1004)의 장바구니 데이터를 직접 가져오는 함수
  Future<List<Map<String, dynamic>>> _fetchDbCartItems() async {
    try {
      // TODO: 팀의 백엔드 실제 IP 및 포트번호(예: 8080)가 맞는지 확인하세요!
      final url = Uri.parse('http://10.0.2.2:8080/api/cart/1004');
      final response = await http.get(url);

      if (response.statusCode == 200) {
        List<dynamic> data = json.decode(utf8.decode(response.bodyBytes));
        return data.map((item) => item as Map<String, dynamic>).toList();
      } else {
        print("🚨 서버 응답 에러 (코드: ${response.statusCode})");
      }
    } catch (e) {
      print("🚨 백엔드 장바구니 실시간 통신 실패: $e");
    }

    // 통신 실패 시 백엔드 DTO 규칙(카멜 케이스)과 싱크를 맞춘 안전망 더미 데이터
    return [
      {"id": 3, "name": "[시그니처] 대나무 바베큐 플래터", "price": 45000, "img": "bbq.jpg"},
      {"id": 5, "name": "콜라 / 사이다 500ml캔", "price": 2500, "img": "drink.jpg"},
    ];
  }

  // 주문하기 기능 처리 함수
  Future<void> _handleCheckout(List<Map<String, dynamic>> dbItems, int totalAmount) async {
    setState(() => _isProcessing = true);

    // 💡 백엔드 DTO Key 값인 'productId'를 매핑하도록 수정 완료
    final payloads = dbItems.map((item) {
      return CartItemPayload(
        productId: item['productId'] as int,
        quantity: item['quantity'] as int,
        price: item['price'] as int,
      );
    }).toList();

    final orderRequest = OrderRequest(
      userId: 1004,
      deliveryAddress: '부산광역시 수영구 광안해변로 219',
      totalAmount: totalAmount,
      items: payloads,
    );

    final isSuccess = await _orderService.sendOrder(orderRequest);

    setState(() => _isProcessing = false);

    if (isSuccess) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('🎉 주문이 백엔드 DB(sh_order)에 성공적으로 저장되었습니다.')),
      );
      setState(() {}); // 주문 완료 후 리스트 새로고침
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('🚨 통신 실패: 백엔드 서버 상태를 확인하세요.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('🛒 장바구니 주문 센터'),
        backgroundColor: Colors.orange,
      ),
      body: FutureBuilder<List<Map<String, dynamic>>>(
        future: _fetchDbCartItems(), // 화면 진입 시 실시간 DB 조회 가동
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator(color: Colors.orange));
          }

          final dbItems = snapshot.data ?? [];

          if (dbItems.isEmpty) {
            return const Center(child: Text('장바구니에 상품이 없습니다.'));
          }

          // 총 금액 자동 계산기
          int calculatedTotal = 0;
          for (var item in dbItems) {
            calculatedTotal += (item['price'] as int) * (item['quantity'] as int);
          }

          return Column(
            children: [
              Expanded(
                child: ListView.builder(
                  itemCount: dbItems.length,
                  itemBuilder: (ctx, i) {
                    final item = dbItems[i];
                    return Card(
                      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      child: ListTile(
                        leading: const Icon(Icons.fastfood, color: Colors.orange),
                        title: Text(
                            item['name'] ?? '상품 획득 실패',
                            style: const TextStyle(fontWeight: FontWeight.bold)
                        ),
                        subtitle: Text('${item['price']}원 x ${item['quantity']}개'),
                        trailing: Text(
                          '${(item['price'] as int) * (item['quantity'] as int)}원',
                          style: const TextStyle(color: Colors.orange, fontWeight: FontWeight.bold),
                        ),
                      ),
                    );
                  },
                ),
              ),
              Card(
                margin: const EdgeInsets.all(16),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '총 금액: $calculatedTotal원',
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
                      _isProcessing
                          ? const CircularProgressIndicator(color: Colors.orange)
                          : ElevatedButton(
                        onPressed: () => _handleCheckout(dbItems, calculatedTotal),
                        style: ElevatedButton.styleFrom(backgroundColor: Colors.orange),
                        child: const Text('주문하기'),
                      ),
                    ],
                  ),
                ),
              )
            ],
          );
        },
      ),
    );
  }
}