package com.girlshop;
import com.girlshop.controller.ProductController;
import com.girlshop.entity.Product;
import com.girlshop.mapper.ProductMapper;
import com.girlshop.mapper.OrderMapper;
import com.girlshop.service.FlashRequestService;
import com.girlshop.dto.UserDTO;
import com.girlshop.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FlashPermissionTest {
 ProductController controller; ProductMapper products; OrderMapper orders; FlashRequestService requests;
 @BeforeEach void setup(){controller=new ProductController();products=mock(ProductMapper.class);orders=mock(OrderMapper.class);requests=mock(FlashRequestService.class);ReflectionTestUtils.setField(controller,"productMapper",products);ReflectionTestUtils.setField(controller,"orderMapper",orders);ReflectionTestUtils.setField(controller,"flashRequests",requests);UserDTO user=new UserDTO();user.setId(10L);UserHolder.saveUser(user);when(products.selectById(1L)).thenReturn(new Product().setId(1L).setUserId(10L).setStatus(1));}
 @AfterEach void cleanup(){UserHolder.removeUser();}
 @Test void ongoingOrderDisablesFlash(){when(orders.selectCount(any())).thenReturn(1L);Map<?,?> result=(Map<?,?>)controller.flashPermission(1L).getData();assertEquals(false,result.get("allowed"));assertTrue(result.get("reason").toString().contains("订单"));}
 @Test void pendingRequestDisablesFlash(){when(orders.selectCount(any())).thenReturn(0L);when(requests.pendingForProduct(1L)).thenReturn(true);assertEquals(false,((Map<?,?>)controller.flashPermission(1L).getData()).get("allowed"));}
 @Test void idleProductAllowsFlash(){when(orders.selectCount(any())).thenReturn(0L);assertEquals(true,((Map<?,?>)controller.flashPermission(1L).getData()).get("allowed"));}
 @Test void otherUserCannotReadPermission(){when(products.selectById(1L)).thenReturn(new Product().setUserId(20L));assertFalse(controller.flashPermission(1L).getSuccess());}
}
