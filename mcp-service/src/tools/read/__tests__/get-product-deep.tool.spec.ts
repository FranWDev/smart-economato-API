import { ZodError } from 'zod';
import { GetProductDeepTool } from '../get-product-deep.tool';
import { InventoryClient } from '../../../mcp/inventory.client';
import { MCP_BASE } from '../../constants';

describe('GetProductDeepTool', () => {
  let tool: GetProductDeepTool;
  let mockClient: Partial<InventoryClient>;

  beforeEach(() => {
    tool = new GetProductDeepTool();
    mockClient = {
      get: jest.fn().mockResolvedValue({ id: 1, name: 'Tomato', stock: 50 }),
    };
  });

  describe('metadata', () => {
    it('should have correct name', () => {
      expect(tool.name).toBe('get_product_deep');
    });

    it('should have matching definition name', () => {
      expect(tool.definition.name).toBe(tool.name);
    });

    it('should require id in inputSchema', () => {
      expect(tool.definition.inputSchema.required).toContain('id');
    });

    it('should have id property in inputSchema', () => {
      expect(tool.definition.inputSchema.properties).toHaveProperty('id');
    });

    it('should have a non-empty description', () => {
      expect(tool.definition.description.length).toBeGreaterThan(10);
    });
  });

  describe('schema validation', () => {
    it('should accept valid args', () => {
      const result = tool.schema.parse({ id: 42 });
      expect(result).toEqual({ id: 42 });
    });

    it('should reject missing id', () => {
      expect(() => tool.schema.parse({})).toThrow(ZodError);
    });

    it('should reject non-number id', () => {
      expect(() => tool.schema.parse({ id: 'abc' })).toThrow(ZodError);
    });

    it('should strip extra properties', () => {
      const result = tool.schema.parse({ id: 1, extra: 'field' });
      expect(result).toEqual({ id: 1 });
    });
  });

  describe('execute', () => {
    it('should call client.get with correct path and jwt', async () => {
      await tool.execute(
        { id: 42 },
        mockClient as InventoryClient,
        'Bearer token123',
      );

      expect(mockClient.get).toHaveBeenCalledWith(
        `${MCP_BASE}/products/42/deep`,
        'Bearer token123',
      );
    });

    it('should return the client response', async () => {
      const result = await tool.execute(
        { id: 1 },
        mockClient as InventoryClient,
        'jwt',
      );

      expect(result).toEqual({ id: 1, name: 'Tomato', stock: 50 });
    });

    it('should propagate client errors', async () => {
      (mockClient.get as jest.Mock).mockRejectedValue(
        new Error('Inventory API Error (404): Product not found'),
      );

      await expect(
        tool.execute({ id: 999 }, mockClient as InventoryClient, 'jwt'),
      ).rejects.toThrow('Inventory API Error (404)');
    });
  });
});
