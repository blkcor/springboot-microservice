package com.chen.orderservice.service;


import com.chen.orderservice.dto.InventoryResponse;
import com.chen.orderservice.dto.OrderLineItemsDto;
import com.chen.orderservice.dto.OrderRequest;
import com.chen.orderservice.model.Order;
import com.chen.orderservice.model.OrderLineItems;
import com.chen.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItems> collect = orderRequest.getOrderLineItemsDtoList().stream().map(this::mapToDto).collect(Collectors.toList());
        order.setOrderLineItemsList(collect);
        List<String> skuCodes = order
                .getOrderLineItemsList()
                .stream()
                .map(OrderLineItems::getSkuCode)
                .collect(Collectors.toList());
        //call inventory service when inventory is in stock
        InventoryResponse[] inventoryResponses = webClientBuilder.build()
                .get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
        boolean allProductIsInStock = Arrays.stream(inventoryResponses).allMatch(InventoryResponse::isInStock);
        if (allProductIsInStock){
            orderRepository.save(order);
            return "place order successfully!";
        }
        else throw new IllegalArgumentException("Product is not in stock, please try again later!");
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        return orderLineItems;
    }
}
