package com.girlshop;

import com.girlshop.dto.*;
import com.girlshop.entity.*;
import com.girlshop.entity.Order;
import com.girlshop.mapper.*;
import com.girlshop.service.IProductService;
import com.girlshop.service.IAddressService;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.girlshop.service.impl.OrderServiceImpl;
import com.girlshop.utils.*;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriorityOneTests {
    @TempDir Path temp;
    OrderServiceImpl service;
    OrderMapper orders;
    ProductMapper products;
    IProductService productService;
    com.girlshop.service.CacheEvictionService evictions;
    @BeforeEach void setup() {
        UserDTO user = new UserDTO(); user.setId(10L); UserHolder.saveUser(user);
        service = spy(new OrderServiceImpl());
        orders = mock(OrderMapper.class); products = mock(ProductMapper.class);
        productService = mock(IProductService.class);
        ReflectionTestUtils.setField(service, "baseMapper", orders);
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "productService", productService);
        evictions=mock(com.girlshop.service.CacheEvictionService.class);ReflectionTestUtils.setField(service,"evictions",evictions);
        ReflectionTestUtils.setField(service,"trade",mock(com.girlshop.service.TradeExperienceService.class));
    }
    @AfterEach void clear() {
        UserHolder.removeUser();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    Result legacyFlash(Long productId, Long addressId) {
        return ReflectionTestUtils.invokeMethod(service,"placeOrder",productId,addressId,true,null,10L);
    }
    Order order(int status) {
        return new Order().setId(1L).setBuyerId(10L).setSellerId(20L).setProductId(2L).setStatus(status);
    }
    @Test void alreadyPaidDoesNotIncrementSalesAgain() {
        when(orders.lockById(1L)).thenReturn(order(1));
        assertFalse(service.pay(1L).getSuccess());
        verifyNoInteractions(productService);
    }
    @Test void alreadyCancelledDoesNotReturnStockAgain() {
        when(orders.lockById(1L)).thenReturn(order(4));
        assertFalse(service.cancel(1L).getSuccess());
        verifyNoInteractions(productService);
    }
    @Test void failedPayTransitionDoesNotIncrementSales() {
        when(orders.lockById(1L)).thenReturn(order(0));
        UpdateChainWrapper<Order> update = mock(UpdateChainWrapper.class, RETURNS_SELF);
        doReturn(update).when(service).update(); when(update.update()).thenReturn(false);
        assertFalse(service.pay(1L).getSuccess()); verifyNoInteractions(productService);
    }
    @Test void failedCancelTransitionDoesNotReturnStock() {
        when(orders.lockById(1L)).thenReturn(order(0));
        UpdateChainWrapper<Order> update = mock(UpdateChainWrapper.class, RETURNS_SELF);
        doReturn(update).when(service).update(); when(update.update()).thenReturn(false);
        assertFalse(service.cancel(1L).getSuccess()); verifyNoInteractions(productService);
    }
    @Test void paidOrderCannotExpire() {
        when(orders.lockById(1L)).thenReturn(order(1).setCreateTime(LocalDateTime.now().minusHours(1)));
        service.expireUnpaid(1L, LocalDateTime.now().minusMinutes(30));
        verifyNoInteractions(productService);
    }
    @Test void deletingOnlyHidesForCurrentUser() {
        when(orders.lockById(1L)).thenReturn(order(3));
        assertTrue(service.delete(1L).getSuccess());
        verify(orders).hide(1L,10L); verify(orders,never()).deleteById(any(java.io.Serializable.class));
    }
    @Test void cannotBuyOwnFlashProduct() {
        when(products.lockById(2L)).thenReturn(new Product().setId(2L).setUserId(10L).setStock(1).setStatus(1));
        assertFalse(legacyFlash(2L,3L).getSuccess()); verifyNoInteractions(productService);
    }
    @Test void missingAddressCannotReserveStock() {
        assertFalse(legacyFlash(2L,null).getSuccess()); verifyNoInteractions(products,productService);
    }
    void prepareFlash() {
        Product p = new Product().setId(2L).setUserId(20L).setStock(1).setStatus(1).setIsFlash(1)
                .setPrice(new java.math.BigDecimal("50.00")).setFlashPrice(new java.math.BigDecimal("20.00"))
                .setFlashBeginTime(LocalDateTime.now().minusHours(1)).setFlashEndTime(LocalDateTime.now().plusHours(1));
        when(products.lockById(2L)).thenReturn(p);
        QueryChainWrapper<Order> query = mock(QueryChainWrapper.class, RETURNS_SELF);
        doReturn(null).when(query).one();
        doReturn(query).when(service).query();
        IAddressService addresses = mock(IAddressService.class);
        when(addresses.getById(3L)).thenReturn(new Address().setId(3L).setUserId(10L));
        ReflectionTestUtils.setField(service,"addressService",addresses);
        RedisIdWorker ids = mock(RedisIdWorker.class); when(ids.nextId("order")).thenReturn(99L);
        ReflectionTestUtils.setField(service,"redisIdWorker",ids);
        UpdateChainWrapper<Product> update = mock(UpdateChainWrapper.class, RETURNS_SELF);
        when(productService.update()).thenReturn(update); when(update.update()).thenReturn(true);
    }
    @Test void flashReturnsPersistedOrderAtFlashPrice() {
        prepareFlash(); TransactionSynchronizationManager.initSynchronization();
        doReturn(true).when(service).save(any(Order.class));
        Result result = legacyFlash(2L,3L);
        assertTrue(result.getSuccess()); assertEquals(99L,result.getData());
        org.mockito.ArgumentCaptor<Order> saved = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(service).save(saved.capture());
        assertEquals(new java.math.BigDecimal("20.00"),saved.getValue().getPrice());
        assertEquals(saved.getValue().getPrice(),saved.getValue().getPayAmount());
        verify(evictions).enqueue(2L);
    }
    @Test void orderSaveFailureThrowsForTransactionRollback() {
        prepareFlash(); doReturn(false).when(service).save(any(Order.class));
        assertThrows(IllegalStateException.class, () -> legacyFlash(2L,3L));
    }
    @Test void endedFlashCannotDeductInventory() {
        when(products.lockById(2L)).thenReturn(new Product().setId(2L).setUserId(20L).setStock(1).setStatus(1).setIsFlash(1)
                .setFlashPrice(java.math.BigDecimal.ONE).setFlashBeginTime(LocalDateTime.now().minusHours(2))
                .setFlashEndTime(LocalDateTime.now().minusHours(1)));
        assertFalse(legacyFlash(2L,3L).getSuccess()); verifyNoInteractions(productService);
    }
    @Test void anotherBuyersAddressCannotDeductInventory() {
        prepareFlash(); IAddressService addresses = mock(IAddressService.class);
        when(addresses.getById(3L)).thenReturn(new Address().setId(3L).setUserId(30L));
        ReflectionTestUtils.setField(service,"addressService",addresses);
        assertFalse(legacyFlash(2L,3L).getSuccess()); verify(productService,never()).update();
    }
    @Test void fakeImageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ImageStorage.save(temp,10L,
                new MockMultipartFile("files","fake.jpg","image/jpeg","hello".getBytes())));
    }
    @Test void imageIsReencodedAndOwned() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",bytes);
        String url = ImageStorage.save(temp,10L,new MockMultipartFile("files","x.exe","application/octet-stream",bytes.toByteArray()));
        assertTrue(url.startsWith("/imgs/products/10/"));
        assertTrue(Files.exists(ImageStorage.ownedPath(temp,10L,url)));
        assertThrows(IllegalArgumentException.class, () -> ImageStorage.ownedPath(temp,20L,url));
    }
    @Test void traversalAndLegacyAssetsCannotBeDeleted() {
        assertThrows(IllegalArgumentException.class, () -> ImageStorage.ownedPath(temp,10L,"../../pom.xml"));
        assertThrows(IllegalArgumentException.class, () -> ImageStorage.ownedPath(temp,10L,"/imgs/白色长款风衣.jpg"));
    }
    @Test void oversizedImageDimensionsRejectedBeforeDecode() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(6001,1,BufferedImage.TYPE_INT_RGB),"png",bytes);
        assertThrows(IllegalArgumentException.class, () -> ImageStorage.save(temp,10L,
                new MockMultipartFile("files","large.png","image/png",bytes.toByteArray())));
    }
}
