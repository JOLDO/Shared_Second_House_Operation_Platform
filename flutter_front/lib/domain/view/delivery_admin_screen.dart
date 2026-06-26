import 'package:flutter/material.dart';

class DeliveryAdminScreen extends StatelessWidget {
  const DeliveryAdminScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('배달 주문 관리 (관리자)'),
        backgroundColor: const Color(0xFF1A1A1A), // 관리자용 다크 테마
      ),
      body: ListView.builder(
        itemCount: 5, // 실제로는 주문 내역 리스트의 길이
        itemBuilder: (context, index) {
          return Card(
            margin: const EdgeInsets.all(10),
            child: ListTile(
              leading: const Icon(Icons.delivery_dining, color: Colors.blue),
              title: Text('주문번호: 2024-000${index + 1}'),
              subtitle: const Text('상태: 배송 준비 중 | 총액: 45,000원'),
              trailing: ElevatedButton(
                onPressed: () {
                  // 주문 상세 보기 또는 상태 변경 로직
                },
                child: const Text('상세보기'),
              ),
            ),
          );
        },
      ),
    );
  }
}