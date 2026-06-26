import 'package:flutter/material.dart';
import 'cart_screen.dart';

class ProductListScreen extends StatefulWidget {
  const ProductListScreen({Key? key}) : super(key: key);

  @override
  State<ProductListScreen> createState() => _ProductListScreenState();
}

class _ProductListScreenState extends State<ProductListScreen> {
  // 실제 API 연동 시 Controller의 데이터를 사용하세요.
  final List<Map<String, dynamic>> products = [
    {"id": 1, "name": "[추천] 오션뷰 세컨하우스 1박 이용권", "price": 150000, "img": "beer.jpg"},
    {"id": 2, "name": "[조식] 수제 샌드위치 & 커피 세트", "price": 12000, "img": "salad.jpg"},
    {"id": 3, "name": "[시그니처] 대나무 바베큐 플래터", "price": 45000, "img": "juice.jpg"},
    {"id": 4, "name": "[가정간편식] 얼큰 차돌된장찌개", "price": 18000, "img": "stew.jpg"},
    {"id": 5, "name": "콜라 / 사이다 500ml캔", "price": 2500, "img": "drink.jpg"},
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('배달 상품 스토어'),
        backgroundColor: const Color(0xFF2E6F40),
        actions: [
          IconButton(
            icon: const Icon(Icons.shopping_basket),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const CartScreen()),
              );
            },
          ),
        ],
      ),
      body: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: products.length,
        itemBuilder: (context, index) {
          final p = products[index];
          return Card(
            elevation: 2,
            margin: const EdgeInsets.only(bottom: 16),
            child: ListTile(
              contentPadding: const EdgeInsets.all(10),
              leading: Container(width: 60, height: 60, color: Colors.grey[200], child: const Icon(Icons.fastfood)),
              title: Text(p['name'], style: const TextStyle(fontWeight: FontWeight.bold)),
              subtitle: Text("${p['price']}원"),
              trailing: ElevatedButton(
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('${p['name']}이 장바구니에 담겼습니다.')),
                  );
                },
                style: ElevatedButton.styleFrom(backgroundColor: Colors.orange),
                child: const Text('담기'),
              ),
            ),
          );
        },
      ),
    );
  }
}