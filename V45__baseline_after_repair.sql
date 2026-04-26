--
-- PostgreSQL database dump
--

\restrict eqYiUi4beXblq1Ghp0uDUxk8A1XyIXvqh9egOanuaBTMVTZfAZGZkAWzumQi1QK

-- Dumped from database version 16.13 (Debian 16.13-1.pgdg12+1)
-- Dumped by pg_dump version 18.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: ims_bs9t_user
--

-- *not* creating schema, since initdb creates it


ALTER SCHEMA public OWNER TO ims_bs9t_user;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: alerts; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.alerts (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    type character varying(50) NOT NULL,
    severity character varying(20) NOT NULL,
    message text NOT NULL,
    resource_id bigint,
    is_dismissed boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now(),
    dismissed_at timestamp without time zone
);


ALTER TABLE public.alerts OWNER TO ims_bs9t_user;

--
-- Name: alerts_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.alerts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.alerts_id_seq OWNER TO ims_bs9t_user;

--
-- Name: alerts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.alerts_id_seq OWNED BY public.alerts.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    tenant_id bigint,
    user_id bigint,
    action character varying(255) NOT NULL,
    details text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.audit_logs OWNER TO ims_bs9t_user;

--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.audit_logs_id_seq OWNER TO ims_bs9t_user;

--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.categories (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp without time zone DEFAULT now(),
    tax_rate numeric(5,2) DEFAULT 0.00
);


ALTER TABLE public.categories OWNER TO ims_bs9t_user;

--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.categories_id_seq OWNER TO ims_bs9t_user;

--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;


--
-- Name: customers; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.customers (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(50),
    email character varying(255),
    address text,
    created_at timestamp without time zone DEFAULT now(),
    gstin character varying(20)
);


ALTER TABLE public.customers OWNER TO ims_bs9t_user;

--
-- Name: customers_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.customers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.customers_id_seq OWNER TO ims_bs9t_user;

--
-- Name: customers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.customers_id_seq OWNED BY public.customers.id;


--
-- Name: email_verifications; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.email_verifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    token character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.email_verifications OWNER TO ims_bs9t_user;

--
-- Name: email_verifications_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.email_verifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.email_verifications_id_seq OWNER TO ims_bs9t_user;

--
-- Name: email_verifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.email_verifications_id_seq OWNED BY public.email_verifications.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO ims_bs9t_user;

--
-- Name: invoices; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.invoices (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    order_id bigint,
    invoice_number character varying(50) NOT NULL,
    amount numeric(12,2) NOT NULL,
    tax_amount numeric(12,2),
    status character varying(20) DEFAULT 'UNPAID'::character varying,
    due_date date,
    paid_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    discount numeric(12,2) DEFAULT 0,
    parent_invoice_id bigint
);


ALTER TABLE public.invoices OWNER TO ims_bs9t_user;

--
-- Name: invoices_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.invoices_id_seq OWNER TO ims_bs9t_user;

--
-- Name: invoices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.invoices_id_seq OWNED BY public.invoices.id;


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(255) NOT NULL,
    message text NOT NULL,
    type character varying(50) NOT NULL,
    resource_id bigint,
    is_read boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.notifications OWNER TO ims_bs9t_user;

--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.notifications_id_seq OWNER TO ims_bs9t_user;

--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.order_items (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    product_id bigint NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(10,2) NOT NULL,
    discount numeric(10,2) DEFAULT 0,
    tax_rate numeric(5,2) DEFAULT 0,
    total numeric(10,2) NOT NULL
);


ALTER TABLE public.order_items OWNER TO ims_bs9t_user;

--
-- Name: order_items_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.order_items_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.order_items_id_seq OWNER TO ims_bs9t_user;

--
-- Name: order_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.order_items_id_seq OWNED BY public.order_items.id;


--
-- Name: orders; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.orders (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    type character varying(20) NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying,
    customer_id bigint,
    supplier_id bigint,
    total_amount numeric(12,2),
    tax_amount numeric(12,2),
    discount numeric(12,2) DEFAULT 0,
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now(),
    reference_order_id bigint,
    CONSTRAINT valid_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'SHIPPED'::character varying, 'COMPLETED'::character varying, 'RECEIVED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.orders OWNER TO ims_bs9t_user;

--
-- Name: orders_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.orders_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.orders_id_seq OWNER TO ims_bs9t_user;

--
-- Name: orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.orders_id_seq OWNED BY public.orders.id;


--
-- Name: payment_gateway_logs; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.payment_gateway_logs (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    payment_id bigint,
    event_type character varying(100),
    raw_payload text,
    created_at timestamp without time zone DEFAULT now(),
    event_id character varying(100)
);


ALTER TABLE public.payment_gateway_logs OWNER TO ims_bs9t_user;

--
-- Name: payment_gateway_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.payment_gateway_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.payment_gateway_logs_id_seq OWNER TO ims_bs9t_user;

--
-- Name: payment_gateway_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.payment_gateway_logs_id_seq OWNED BY public.payment_gateway_logs.id;


--
-- Name: payments; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.payments (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    payment_mode character varying(30),
    reference character varying(100),
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now(),
    gateway_transaction_id character varying(255),
    status character varying(50) DEFAULT 'PENDING'::character varying
);


ALTER TABLE public.payments OWNER TO ims_bs9t_user;

--
-- Name: payments_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.payments_id_seq OWNER TO ims_bs9t_user;

--
-- Name: payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.payments_id_seq OWNED BY public.payments.id;


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.permissions (
    id bigint NOT NULL,
    key character varying(100) NOT NULL,
    description character varying(255) NOT NULL
);


ALTER TABLE public.permissions OWNER TO ims_bs9t_user;

--
-- Name: permissions_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.permissions_id_seq OWNER TO ims_bs9t_user;

--
-- Name: permissions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.permissions_id_seq OWNED BY public.permissions.id;


--
-- Name: pharmacy_products; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.pharmacy_products (
    product_id bigint NOT NULL,
    batch_number character varying(100),
    expiry_date date NOT NULL,
    manufacturer character varying(255),
    hsn_code character varying(50),
    schedule character varying(10)
);


ALTER TABLE public.pharmacy_products OWNER TO ims_bs9t_user;

--
-- Name: platform_invites; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.platform_invites (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    role character varying(50) NOT NULL,
    token character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used_at timestamp without time zone,
    created_by bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.platform_invites OWNER TO ims_bs9t_user;

--
-- Name: platform_invites_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.platform_invites_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.platform_invites_id_seq OWNER TO ims_bs9t_user;

--
-- Name: platform_invites_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.platform_invites_id_seq OWNED BY public.platform_invites.id;


--
-- Name: products; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.products (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    sku character varying(100),
    barcode character varying(100),
    category_id bigint,
    unit character varying(50),
    purchase_price numeric(10,2),
    sale_price numeric(10,2) NOT NULL,
    stock integer DEFAULT 0,
    reorder_level integer DEFAULT 10,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    version bigint DEFAULT 0
);


ALTER TABLE public.products OWNER TO ims_bs9t_user;

--
-- Name: products_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.products_id_seq OWNER TO ims_bs9t_user;

--
-- Name: products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.products_id_seq OWNED BY public.products.id;


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


ALTER TABLE public.role_permissions OWNER TO ims_bs9t_user;

--
-- Name: roles; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    tenant_id bigint,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.roles OWNER TO ims_bs9t_user;

--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.roles_id_seq OWNER TO ims_bs9t_user;

--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: stock_movements; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.stock_movements (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    movement_type character varying(30) NOT NULL,
    quantity integer NOT NULL,
    previous_stock integer,
    new_stock integer,
    reference_id bigint,
    reference_type character varying(30),
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.stock_movements OWNER TO ims_bs9t_user;

--
-- Name: stock_movements_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.stock_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.stock_movements_id_seq OWNER TO ims_bs9t_user;

--
-- Name: stock_movements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.stock_movements_id_seq OWNED BY public.stock_movements.id;


--
-- Name: subscription_plans; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.subscription_plans (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    price numeric(12,2) DEFAULT 0,
    currency character varying(10) DEFAULT 'INR'::character varying,
    billing_cycle character varying(20) NOT NULL,
    duration_days integer DEFAULT 30,
    features text,
    max_users integer DEFAULT 0,
    max_products integer DEFAULT 0,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    version integer DEFAULT 1,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.subscription_plans OWNER TO ims_bs9t_user;

--
-- Name: subscription_plans_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.subscription_plans_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.subscription_plans_id_seq OWNER TO ims_bs9t_user;

--
-- Name: subscription_plans_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.subscription_plans_id_seq OWNED BY public.subscription_plans.id;


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.subscriptions (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    plan character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    start_date timestamp without time zone NOT NULL,
    end_date timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.subscriptions OWNER TO ims_bs9t_user;

--
-- Name: subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.subscriptions_id_seq OWNER TO ims_bs9t_user;

--
-- Name: subscriptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.subscriptions_id_seq OWNED BY public.subscriptions.id;


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.suppliers (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(50),
    email character varying(255),
    address text,
    gstin character varying(20),
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.suppliers OWNER TO ims_bs9t_user;

--
-- Name: suppliers_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.suppliers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.suppliers_id_seq OWNER TO ims_bs9t_user;

--
-- Name: suppliers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.suppliers_id_seq OWNED BY public.suppliers.id;


--
-- Name: support_attachments; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.support_attachments (
    id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    file_url character varying(500) NOT NULL,
    uploaded_by bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.support_attachments OWNER TO ims_bs9t_user;

--
-- Name: support_attachments_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.support_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.support_attachments_id_seq OWNER TO ims_bs9t_user;

--
-- Name: support_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.support_attachments_id_seq OWNED BY public.support_attachments.id;


--
-- Name: support_messages; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.support_messages (
    id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    sender_id bigint NOT NULL,
    sender_type character varying(20) NOT NULL,
    message text NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.support_messages OWNER TO ims_bs9t_user;

--
-- Name: support_messages_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.support_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.support_messages_id_seq OWNER TO ims_bs9t_user;

--
-- Name: support_messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.support_messages_id_seq OWNED BY public.support_messages.id;


--
-- Name: support_tickets; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.support_tickets (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    created_by bigint NOT NULL,
    title character varying(255) NOT NULL,
    description text NOT NULL,
    priority character varying(20) DEFAULT 'MEDIUM'::character varying,
    status character varying(30) DEFAULT 'OPEN'::character varying,
    category character varying(30) DEFAULT 'GENERAL'::character varying,
    assigned_to bigint,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.support_tickets OWNER TO ims_bs9t_user;

--
-- Name: support_tickets_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.support_tickets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.support_tickets_id_seq OWNER TO ims_bs9t_user;

--
-- Name: support_tickets_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.support_tickets_id_seq OWNED BY public.support_tickets.id;


--
-- Name: system_configs; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.system_configs (
    config_key character varying(100) NOT NULL,
    config_value character varying(255) NOT NULL,
    description text,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.system_configs OWNER TO ims_bs9t_user;

--
-- Name: tenants; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.tenants (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    workspace_slug character varying(255),
    business_type character varying(50) NOT NULL,
    plan character varying(50) DEFAULT 'FREE'::character varying,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    invoice_sequence integer DEFAULT 0,
    max_products integer,
    max_users integer,
    expiry_threshold_days integer DEFAULT 30,
    address text,
    gstin character varying(20),
    company_code character varying(20) NOT NULL,
    version bigint DEFAULT 0,
    webhook_secret character varying(255)
);


ALTER TABLE public.tenants OWNER TO ims_bs9t_user;

--
-- Name: tenants_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.tenants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.tenants_id_seq OWNER TO ims_bs9t_user;

--
-- Name: tenants_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.tenants_id_seq OWNED BY public.tenants.id;


--
-- Name: transfer_orders; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.transfer_orders (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    from_location character varying(100) NOT NULL,
    to_location character varying(100) NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying,
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now(),
    product_id bigint,
    quantity integer DEFAULT 1
);


ALTER TABLE public.transfer_orders OWNER TO ims_bs9t_user;

--
-- Name: transfer_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.transfer_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.transfer_orders_id_seq OWNER TO ims_bs9t_user;

--
-- Name: transfer_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.transfer_orders_id_seq OWNED BY public.transfer_orders.id;


--
-- Name: user_permissions; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.user_permissions (
    user_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


ALTER TABLE public.user_permissions OWNER TO ims_bs9t_user;

--
-- Name: users; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    role character varying(50) NOT NULL,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    scope character varying(20) DEFAULT 'TENANT'::character varying,
    reset_token character varying(255),
    reset_token_expiry timestamp without time zone,
    last_login timestamp without time zone,
    phone character varying(20),
    is_verified boolean DEFAULT false,
    is_platform_user boolean DEFAULT false,
    version bigint DEFAULT 0,
    verification_token character varying(255),
    verification_token_expiry timestamp without time zone
);


ALTER TABLE public.users OWNER TO ims_bs9t_user;

--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.users_id_seq OWNER TO ims_bs9t_user;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: warehouse_products; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.warehouse_products (
    product_id bigint NOT NULL,
    storage_location character varying(100),
    zone character varying(50),
    rack character varying(50),
    bin character varying(50)
);


ALTER TABLE public.warehouse_products OWNER TO ims_bs9t_user;

--
-- Name: webhooks; Type: TABLE; Schema: public; Owner: ims_bs9t_user
--

CREATE TABLE public.webhooks (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    url text NOT NULL,
    secret character varying(255),
    event_types text NOT NULL,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.webhooks OWNER TO ims_bs9t_user;

--
-- Name: webhooks_id_seq; Type: SEQUENCE; Schema: public; Owner: ims_bs9t_user
--

CREATE SEQUENCE public.webhooks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.webhooks_id_seq OWNER TO ims_bs9t_user;

--
-- Name: webhooks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ims_bs9t_user
--

ALTER SEQUENCE public.webhooks_id_seq OWNED BY public.webhooks.id;


--
-- Name: alerts id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.alerts ALTER COLUMN id SET DEFAULT nextval('public.alerts_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: categories id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);


--
-- Name: customers id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.customers ALTER COLUMN id SET DEFAULT nextval('public.customers_id_seq'::regclass);


--
-- Name: email_verifications id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.email_verifications ALTER COLUMN id SET DEFAULT nextval('public.email_verifications_id_seq'::regclass);


--
-- Name: invoices id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.invoices ALTER COLUMN id SET DEFAULT nextval('public.invoices_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Name: order_items id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.order_items ALTER COLUMN id SET DEFAULT nextval('public.order_items_id_seq'::regclass);


--
-- Name: orders id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.orders ALTER COLUMN id SET DEFAULT nextval('public.orders_id_seq'::regclass);


--
-- Name: payment_gateway_logs id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payment_gateway_logs ALTER COLUMN id SET DEFAULT nextval('public.payment_gateway_logs_id_seq'::regclass);


--
-- Name: payments id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payments ALTER COLUMN id SET DEFAULT nextval('public.payments_id_seq'::regclass);


--
-- Name: permissions id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.permissions ALTER COLUMN id SET DEFAULT nextval('public.permissions_id_seq'::regclass);


--
-- Name: platform_invites id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.platform_invites ALTER COLUMN id SET DEFAULT nextval('public.platform_invites_id_seq'::regclass);


--
-- Name: products id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.products ALTER COLUMN id SET DEFAULT nextval('public.products_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: stock_movements id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.stock_movements ALTER COLUMN id SET DEFAULT nextval('public.stock_movements_id_seq'::regclass);


--
-- Name: subscription_plans id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.subscription_plans ALTER COLUMN id SET DEFAULT nextval('public.subscription_plans_id_seq'::regclass);


--
-- Name: subscriptions id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.subscriptions ALTER COLUMN id SET DEFAULT nextval('public.subscriptions_id_seq'::regclass);


--
-- Name: suppliers id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.suppliers ALTER COLUMN id SET DEFAULT nextval('public.suppliers_id_seq'::regclass);


--
-- Name: support_attachments id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_attachments ALTER COLUMN id SET DEFAULT nextval('public.support_attachments_id_seq'::regclass);


--
-- Name: support_messages id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_messages ALTER COLUMN id SET DEFAULT nextval('public.support_messages_id_seq'::regclass);


--
-- Name: support_tickets id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_tickets ALTER COLUMN id SET DEFAULT nextval('public.support_tickets_id_seq'::regclass);


--
-- Name: tenants id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.tenants ALTER COLUMN id SET DEFAULT nextval('public.tenants_id_seq'::regclass);


--
-- Name: transfer_orders id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.transfer_orders ALTER COLUMN id SET DEFAULT nextval('public.transfer_orders_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: webhooks id; Type: DEFAULT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.webhooks ALTER COLUMN id SET DEFAULT nextval('public.webhooks_id_seq'::regclass);


--
-- Name: alerts alerts_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.alerts
    ADD CONSTRAINT alerts_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);


--
-- Name: email_verifications email_verifications_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.email_verifications
    ADD CONSTRAINT email_verifications_pkey PRIMARY KEY (id);


--
-- Name: email_verifications email_verifications_token_key; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.email_verifications
    ADD CONSTRAINT email_verifications_token_key UNIQUE (token);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: payment_gateway_logs payment_gateway_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payment_gateway_logs
    ADD CONSTRAINT payment_gateway_logs_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_key_key; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_key_key UNIQUE (key);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: pharmacy_products pharmacy_products_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.pharmacy_products
    ADD CONSTRAINT pharmacy_products_pkey PRIMARY KEY (product_id);


--
-- Name: platform_invites platform_invites_email_key; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.platform_invites
    ADD CONSTRAINT platform_invites_email_key UNIQUE (email);


--
-- Name: platform_invites platform_invites_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.platform_invites
    ADD CONSTRAINT platform_invites_pkey PRIMARY KEY (id);


--
-- Name: platform_invites platform_invites_token_key; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.platform_invites
    ADD CONSTRAINT platform_invites_token_key UNIQUE (token);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: stock_movements stock_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_pkey PRIMARY KEY (id);


--
-- Name: subscription_plans subscription_plans_name_key; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.subscription_plans
    ADD CONSTRAINT subscription_plans_name_key UNIQUE (name);


--
-- Name: subscription_plans subscription_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.subscription_plans
    ADD CONSTRAINT subscription_plans_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);


--
-- Name: support_attachments support_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_attachments
    ADD CONSTRAINT support_attachments_pkey PRIMARY KEY (id);


--
-- Name: support_messages support_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_messages
    ADD CONSTRAINT support_messages_pkey PRIMARY KEY (id);


--
-- Name: support_tickets support_tickets_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_pkey PRIMARY KEY (id);


--
-- Name: system_configs system_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.system_configs
    ADD CONSTRAINT system_configs_pkey PRIMARY KEY (config_key);


--
-- Name: tenants tenants_domain_key; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_domain_key UNIQUE (workspace_slug);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: transfer_orders transfer_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.transfer_orders
    ADD CONSTRAINT transfer_orders_pkey PRIMARY KEY (id);


--
-- Name: tenants uk_tenants_company_code; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT uk_tenants_company_code UNIQUE (company_code);


--
-- Name: user_permissions user_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT user_permissions_pkey PRIMARY KEY (user_id, permission_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: warehouse_products warehouse_products_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.warehouse_products
    ADD CONSTRAINT warehouse_products_pkey PRIMARY KEY (product_id);


--
-- Name: webhooks webhooks_pkey; Type: CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.webhooks
    ADD CONSTRAINT webhooks_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_alerts_is_dismissed; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_alerts_is_dismissed ON public.alerts USING btree (is_dismissed);


--
-- Name: idx_alerts_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_alerts_tenant ON public.alerts USING btree (tenant_id);


--
-- Name: idx_alerts_type; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_alerts_type ON public.alerts USING btree (type);


--
-- Name: idx_audit_logs_action; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_audit_logs_action ON public.audit_logs USING btree (action);


--
-- Name: idx_audit_logs_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_audit_logs_tenant ON public.audit_logs USING btree (tenant_id);


--
-- Name: idx_categories_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_categories_tenant ON public.categories USING btree (tenant_id);


--
-- Name: idx_customers_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_customers_tenant ON public.customers USING btree (tenant_id);


--
-- Name: idx_invoices_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_invoices_tenant ON public.invoices USING btree (tenant_id);


--
-- Name: idx_notifications_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_notifications_tenant ON public.notifications USING btree (tenant_id);


--
-- Name: idx_notifications_user; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_notifications_user ON public.notifications USING btree (user_id);


--
-- Name: idx_orders_created; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_orders_created ON public.orders USING btree (tenant_id, created_at);


--
-- Name: idx_orders_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_orders_tenant ON public.orders USING btree (tenant_id);


--
-- Name: idx_orders_tenant_status_date; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_orders_tenant_status_date ON public.orders USING btree (tenant_id, status, created_at);


--
-- Name: idx_orders_tenant_type_date; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_orders_tenant_type_date ON public.orders USING btree (tenant_id, type, created_at);


--
-- Name: idx_orders_type; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_orders_type ON public.orders USING btree (tenant_id, type);


--
-- Name: idx_payments_invoice; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_payments_invoice ON public.payments USING btree (invoice_id);


--
-- Name: idx_payments_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_payments_tenant ON public.payments USING btree (tenant_id);


--
-- Name: idx_pg_logs_event_id; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE UNIQUE INDEX idx_pg_logs_event_id ON public.payment_gateway_logs USING btree (event_id);


--
-- Name: idx_pg_logs_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_pg_logs_tenant ON public.payment_gateway_logs USING btree (tenant_id);


--
-- Name: idx_pharmacy_expiry; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_pharmacy_expiry ON public.pharmacy_products USING btree (expiry_date);


--
-- Name: idx_products_barcode; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_barcode ON public.products USING btree (tenant_id, barcode);


--
-- Name: idx_products_search; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_search ON public.products USING gin (to_tsvector('english'::regconfig, (((((COALESCE(name, ''::character varying))::text || ' '::text) || (COALESCE(sku, ''::character varying))::text) || ' '::text) || (COALESCE(barcode, ''::character varying))::text)));


--
-- Name: idx_products_sku; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_sku ON public.products USING btree (tenant_id, sku);


--
-- Name: idx_products_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_tenant ON public.products USING btree (tenant_id);


--
-- Name: idx_products_tenant_active; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_tenant_active ON public.products USING btree (tenant_id, is_active);


--
-- Name: idx_products_tenant_category; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_tenant_category ON public.products USING btree (tenant_id, category_id, is_active);


--
-- Name: idx_products_tenant_id; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_tenant_id ON public.products USING btree (tenant_id, id);


--
-- Name: idx_products_tenant_stock; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_tenant_stock ON public.products USING btree (tenant_id, stock);


--
-- Name: idx_products_tenant_stock_reorder; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_products_tenant_stock_reorder ON public.products USING btree (tenant_id, stock, reorder_level) WHERE (is_active = true);


--
-- Name: idx_roles_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_roles_tenant ON public.roles USING btree (tenant_id);


--
-- Name: idx_stock_movements_date; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_stock_movements_date ON public.stock_movements USING btree (tenant_id, created_at);


--
-- Name: idx_stock_movements_product; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_stock_movements_product ON public.stock_movements USING btree (tenant_id, product_id);


--
-- Name: idx_stock_movements_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_stock_movements_tenant ON public.stock_movements USING btree (tenant_id);


--
-- Name: idx_subscriptions_status; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_subscriptions_status ON public.subscriptions USING btree (status);


--
-- Name: idx_subscriptions_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_subscriptions_tenant ON public.subscriptions USING btree (tenant_id);


--
-- Name: idx_suppliers_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_suppliers_tenant ON public.suppliers USING btree (tenant_id);


--
-- Name: idx_support_attachments_ticket; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_support_attachments_ticket ON public.support_attachments USING btree (ticket_id);


--
-- Name: idx_support_messages_ticket; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_support_messages_ticket ON public.support_messages USING btree (ticket_id);


--
-- Name: idx_support_tickets_assigned; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_support_tickets_assigned ON public.support_tickets USING btree (assigned_to);


--
-- Name: idx_support_tickets_created_by; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_support_tickets_created_by ON public.support_tickets USING btree (created_by);


--
-- Name: idx_support_tickets_status; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_support_tickets_status ON public.support_tickets USING btree (status);


--
-- Name: idx_support_tickets_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_support_tickets_tenant ON public.support_tickets USING btree (tenant_id);


--
-- Name: idx_user_tenant_active; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_user_tenant_active ON public.users USING btree (tenant_id, is_active);


--
-- Name: idx_users_scope; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_users_scope ON public.users USING btree (scope);


--
-- Name: idx_users_tenant_reset_token_expiry; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_users_tenant_reset_token_expiry ON public.users USING btree (tenant_id, reset_token_expiry);


--
-- Name: idx_webhooks_tenant; Type: INDEX; Schema: public; Owner: ims_bs9t_user
--

CREATE INDEX idx_webhooks_tenant ON public.webhooks USING btree (tenant_id);


--
-- Name: alerts alerts_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.alerts
    ADD CONSTRAINT alerts_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: customers customers_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: email_verifications email_verifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.email_verifications
    ADD CONSTRAINT email_verifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: categories fk_categories_tenant; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT fk_categories_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: user_permissions fk_user_perms_perm; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT fk_user_perms_perm FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;


--
-- Name: user_permissions fk_user_perms_user; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT fk_user_perms_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: users fk_users_tenant; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: invoices invoices_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: invoices invoices_parent_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_parent_invoice_id_fkey FOREIGN KEY (parent_invoice_id) REFERENCES public.invoices(id);


--
-- Name: invoices invoices_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: notifications notifications_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: order_items order_items_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: order_items order_items_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: orders orders_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: orders orders_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: orders orders_reference_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_reference_order_id_fkey FOREIGN KEY (reference_order_id) REFERENCES public.orders(id);


--
-- Name: orders orders_supplier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: orders orders_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: payment_gateway_logs payment_gateway_logs_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payment_gateway_logs
    ADD CONSTRAINT payment_gateway_logs_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id);


--
-- Name: payment_gateway_logs payment_gateway_logs_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payment_gateway_logs
    ADD CONSTRAINT payment_gateway_logs_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: payments payments_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: payments payments_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);


--
-- Name: payments payments_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: pharmacy_products pharmacy_products_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.pharmacy_products
    ADD CONSTRAINT pharmacy_products_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;


--
-- Name: platform_invites platform_invites_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.platform_invites
    ADD CONSTRAINT platform_invites_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: products products_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id);


--
-- Name: products products_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;


--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: roles roles_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: stock_movements stock_movements_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: stock_movements stock_movements_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: subscriptions subscriptions_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: suppliers suppliers_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: support_attachments support_attachments_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_attachments
    ADD CONSTRAINT support_attachments_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES public.support_tickets(id);


--
-- Name: support_attachments support_attachments_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_attachments
    ADD CONSTRAINT support_attachments_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES public.users(id);


--
-- Name: support_messages support_messages_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_messages
    ADD CONSTRAINT support_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.users(id);


--
-- Name: support_messages support_messages_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_messages
    ADD CONSTRAINT support_messages_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES public.support_tickets(id);


--
-- Name: support_tickets support_tickets_assigned_to_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES public.users(id);


--
-- Name: support_tickets support_tickets_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: support_tickets support_tickets_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: transfer_orders transfer_orders_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.transfer_orders
    ADD CONSTRAINT transfer_orders_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: transfer_orders transfer_orders_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.transfer_orders
    ADD CONSTRAINT transfer_orders_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: transfer_orders transfer_orders_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.transfer_orders
    ADD CONSTRAINT transfer_orders_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: warehouse_products warehouse_products_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.warehouse_products
    ADD CONSTRAINT warehouse_products_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;


--
-- Name: webhooks webhooks_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ims_bs9t_user
--

ALTER TABLE ONLY public.webhooks
    ADD CONSTRAINT webhooks_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: DEFAULT PRIVILEGES FOR SEQUENCES; Type: DEFAULT ACL; Schema: -; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT ALL ON SEQUENCES TO ims_bs9t_user;


--
-- Name: DEFAULT PRIVILEGES FOR TYPES; Type: DEFAULT ACL; Schema: -; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT ALL ON TYPES TO ims_bs9t_user;


--
-- Name: DEFAULT PRIVILEGES FOR FUNCTIONS; Type: DEFAULT ACL; Schema: -; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT ALL ON FUNCTIONS TO ims_bs9t_user;


--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: -; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLES TO ims_bs9t_user;


--
-- PostgreSQL database dump complete
--

\unrestrict eqYiUi4beXblq1Ghp0uDUxk8A1XyIXvqh9egOanuaBTMVTZfAZGZkAWzumQi1QK

