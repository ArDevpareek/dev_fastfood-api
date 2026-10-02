package com.dev.fastfood.service;

import com.dev.fastfood.entity.Restaurant;
import com.dev.fastfood.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Covers the service-layer logic only. The @CacheEvict on createRestaurant
// is a Spring AOP proxy concern — it only actually runs behind a real
// Spring context with caching enabled, not behind Mockito's @InjectMocks,
// so it can't be asserted here. Same approach the Milestone 2 caching
// behavior used: proxy behavior is verified live (see README), this test
// covers what createRestaurant() itself does.
@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    @Test
    void createRestaurant_savesRestaurantWithGivenFields() {
        when(restaurantRepository.save(org.mockito.ArgumentMatchers.any(Restaurant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Restaurant result = restaurantService.createRestaurant(
                "Sagar Ratna", "North Indian comfort food", "North Indian",
                new BigDecimal("30.00"), new BigDecimal("150.00"));

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        Restaurant saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Sagar Ratna");
        assertThat(saved.getDescription()).isEqualTo("North Indian comfort food");
        assertThat(saved.getCuisine()).isEqualTo("North Indian");
        assertThat(saved.getDeliveryFee()).isEqualTo(new BigDecimal("30.00"));
        assertThat(saved.getMinOrderAmount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(result).isSameAs(saved);
    }

    @Test
    void createRestaurant_generatesANewIdEveryTime() {
        when(restaurantRepository.save(org.mockito.ArgumentMatchers.any(Restaurant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Restaurant first = restaurantService.createRestaurant(
                "A", null, null, BigDecimal.ZERO, BigDecimal.ZERO);
        Restaurant second = restaurantService.createRestaurant(
                "B", null, null, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }
}
