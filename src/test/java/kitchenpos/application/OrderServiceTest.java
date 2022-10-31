package kitchenpos.application;

import static kitchenpos.support.DomainFixture.빈_테이블_생성;
import static kitchenpos.support.DomainFixture.뿌링클;
import static kitchenpos.support.DomainFixture.뿌링클_치즈볼_메뉴_생성;
import static kitchenpos.support.DomainFixture.세트_메뉴;
import static kitchenpos.support.DomainFixture.채워진_테이블_생성;
import static kitchenpos.support.DomainFixture.치즈볼;
import static kitchenpos.support.DomainFixture.한개;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import java.util.List;
import kitchenpos.application.OrderService;
import kitchenpos.exception.CustomErrorCode;
import kitchenpos.exception.DomainLogicException;
import kitchenpos.exception.NotFoundException;
import kitchenpos.domain.Menu;
import kitchenpos.repository.MenuGroupRepository;
import kitchenpos.repository.MenuRepository;
import kitchenpos.repository.ProductRepository;
import kitchenpos.domain.Order;
import kitchenpos.domain.OrderLineItem;
import kitchenpos.domain.OrderStatus;
import kitchenpos.domain.OrderTable;
import kitchenpos.repository.OrderRepository;
import kitchenpos.repository.OrderTableRepository;
import kitchenpos.ui.dto.OrderChangeStatusRequest;
import kitchenpos.ui.dto.OrderCreateRequest;
import kitchenpos.ui.dto.OrderLineItemCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Sql(scripts = "classpath:truncate.sql")
class OrderServiceTest {

    private final ProductRepository productRepository;
    private final MenuGroupRepository menuGroupRepository;
    private final MenuRepository menuRepository;
    private final OrderTableRepository orderTableRepository;
    private final OrderRepository orderRepository;

    private final OrderService orderService;

    @Autowired
    public OrderServiceTest(final ProductRepository productRepository,
                            final MenuGroupRepository menuGroupRepository,
                            final MenuRepository menuRepository,
                            final OrderTableRepository orderTableRepository,
                            final OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.menuGroupRepository = menuGroupRepository;
        this.menuRepository = menuRepository;
        this.orderTableRepository = orderTableRepository;
        this.orderRepository = orderRepository;
        this.orderService = new OrderService(orderTableRepository, orderRepository);
    }

    private Menu menu;
    private OrderTable table;

    @BeforeEach
    void setUp() {
        final var productA = productRepository.save(뿌링클);
        final var productB = productRepository.save(치즈볼);
        final var menuGroup = menuGroupRepository.save(세트_메뉴);
        menu = menuRepository.save(뿌링클_치즈볼_메뉴_생성(menuGroup.getId(), productA, productB));
        table = orderTableRepository.save(채워진_테이블_생성());
    }

    @Test
    void 주문을_생성하고_결과를_반환한다() {
        // given
        final var request = new OrderCreateRequest(table.getId(),
                List.of(new OrderLineItemCreateRequest(menu.getId(), 1)));

        // when
        final var response = orderService.create(request);

        // then
        assertAll(
                () -> assertThat(response.getId()).isNotNull(),
                () -> assertThat(response.getOrderTableId()).isEqualTo(table.getId()),
                () -> assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.COOKING.name()),
                () -> assertThat(response.getOrderedTime()).isBefore(LocalDateTime.now()),
                () -> assertThat(response.getOrderLineItems()).hasSize(1)
        );
    }

    @Test
    void 주문_항목이_비어있으면_예외를_던진다() {
        // given
        final var request = new OrderCreateRequest(table.getId(), List.of());

        // when & then
        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(DomainLogicException.class)
                .extracting("errorCode")
                .isEqualTo(CustomErrorCode.ORDER_ITEM_EMPTY_ERROR);
    }

    @Test
    void 없는_메뉴인_경우_예외를_던진다() {
        // given
        final var request = new OrderCreateRequest(table.getId(),
                List.of(new OrderLineItemCreateRequest(0L, 1)));

        // when & then
        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 없는_테이블인_경우_예외를_던진다() {
        // given
        final var request = new OrderCreateRequest(100L,
                List.of(new OrderLineItemCreateRequest(menu.getId(), 1)));

        // when & then
        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(CustomErrorCode.TABLE_NOT_FOUND_ERROR);
    }

    @Test
    void 빈_테이블인_경우_예외를_던진다() {
        // given
        final var emptyTable = orderTableRepository.save(빈_테이블_생성());
        final var request = new OrderCreateRequest(emptyTable.getId(),
                List.of(new OrderLineItemCreateRequest(menu.getId(), 1)));

        // when & then
        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(DomainLogicException.class)
                .extracting("errorCode")
                .isEqualTo(CustomErrorCode.ORDER_TABLE_EMPTY_ERROR);
    }

    @Test
    void 주문_목록을_조회한다() {
        // given
        orderRepository.save(new Order(null, table, OrderStatus.COOKING, LocalDateTime.now(),
                List.of(new OrderLineItem(menu.getId(), 한개))));
        orderRepository.save(new Order(null, table, OrderStatus.COOKING, LocalDateTime.now(),
                List.of(new OrderLineItem(menu.getId(), 한개))));

        // when
        final var foundOrders = orderService.list();

        // then
        assertThat(foundOrders).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void 주문_상태를_식사로_변경한다() {
        // given
        final var orderId = orderRepository.save(new Order(null, table, OrderStatus.COOKING, LocalDateTime.now(),
                List.of(new OrderLineItem(menu.getId(), 한개)))).getId();
        final var request = new OrderChangeStatusRequest(OrderStatus.MEAL.name());

        // when
        final var response = orderService.changeOrderStatus(orderId, request);

        // then
        assertAll(
                () -> assertThat(response.getId()).isEqualTo(orderId),
                () -> assertThat(response.getOrderStatus()).isEqualTo(request.getOrderStatus())
        );
    }

    @Test
    void 주문_상태를_계산완료로_변경한다() {
        // given
        final var orderId = orderRepository.save(new Order(null, table, OrderStatus.COOKING, LocalDateTime.now(),
                List.of(new OrderLineItem(menu.getId(), 한개)))).getId();
        final var request = new OrderChangeStatusRequest(OrderStatus.COMPLETION.name());

        // when
        final var changedOrder = orderService.changeOrderStatus(orderId, request);

        // then
        assertAll(
                () -> assertThat(changedOrder.getId()).isEqualTo(orderId),
                () -> assertThat(changedOrder.getOrderStatus()).isEqualTo(request.getOrderStatus())
        );
    }

    @Test
    void 없는_주문일_경우_예외를_던진다() {
        // given
        final var request = new OrderChangeStatusRequest(OrderStatus.MEAL.name());

        // when & then
        assertThatThrownBy(() -> orderService.changeOrderStatus(0L, request))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(CustomErrorCode.ORDER_NOT_FOUND_ERROR);
    }

    @Test
    void 주문_상태가_이미_계산완료인_경우_예외를_던진다() {
        // given
        final var orderId = orderRepository.save(new Order(null, table, OrderStatus.COMPLETION, LocalDateTime.now(),
                List.of(new OrderLineItem(menu.getId(), 한개)))).getId();
        final var request = new OrderChangeStatusRequest(OrderStatus.COMPLETION.name());

        // when & then
        assertThatThrownBy(() -> orderService.changeOrderStatus(orderId, request))
                .isInstanceOf(DomainLogicException.class)
                .extracting("errorCode")
                .isEqualTo(CustomErrorCode.ORDER_STATUS_ALREADY_COMPLETED_ERROR);
    }
}